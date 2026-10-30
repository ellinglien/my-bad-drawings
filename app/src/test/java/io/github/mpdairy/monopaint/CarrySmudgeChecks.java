package io.github.mpdairy.monopaint;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Smudge (Carry: Lift, Drag, Smear), the Smudge spec's desktop checks: codes, golden pages against the web lab, the
 * acceptance numbers, own layer only, no halos, the page as the pen found it (quick strokes), one Undo, settling
 * first, gravity, the eraser, cores, rolls and a sweep. {@code java CarrySmudgeChecks pairs <folder>} writes each
 * golden beside the app's drawing of it, as PNGs (scripts/smudge_pictures.sh).
 */
public final class CarrySmudgeChecks {
    public static void main(String[] args) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        if (args.length > 1 && args[0].equals("pairs")) { pairs(new File(args[1])); return; }
        verbose = args.length > 0;
        codes(); goldens(); acceptance(); quickRule(); ownLayer(); noHalos(); penDownPage(); undo(); settlesFirst(); gravity(); eraser(); cores(); rolls(); sweep();
        System.out.println("PASS: Smudge's codes round-trip byte for byte; six brushes draw the web lab's golden pages (at 16 greys, at most 0.1% of pixels one grey off); a finger lays only its smudge and goes quick past the panel's reach, showing its footprint; a smear drags a line a brush width, a stump softens hatching without darkening it, Lift lightens a wash and greys gouache over dark, Drag bends a line; a smudge moves only its own layer, never lightens the wash under an ink smear, reads the page as found when drawn quick, is one Undo, comes after what still settled, ignores the page's tilt, is erased as any mark, draws the same on several cores; Smudge rolls after every part at the lab's weights, every other part as before; every tip, medium, smudge and drying draws");
    }
    static boolean verbose;
    static void note(String s) { if (verbose) System.out.println(s); }

    static final int W = 480, H = 360;
    /** The golden brushes (scripts/carry_goldens.cjs lists the same): name, code, size px, darkness; Own paint 0, or no darkness. */
    static final Object[][] GOLDENS = {
            {"pencil-smear", "pencil-smear.M40i074000000at_4ee00_3p6js00_000_0_0000000000000000000nz0.1wyw208", 6.0, 1.0},
            {"charcoal-smear", "charcoal-smear.M50i000000000at_3atee_3p6js00_000_0_0000000000000000000nz0.13ax4gw", 30.0, 1.0},
            {"stump", "stump.M50i00000000yat_3eqlj_3p6ll00_1nl_1jz_8pthh00x9is00000000nz0.51j8ud", 28.0, 1.0},
            {"wash-lift", "wash-lift.M60i000000000bv_1ul0000000000_177at_000_0_0000000000000000000nz0.1dn6o7h", 60.0, 0.0},
            {"oils-drag", "oils-drag.M90i00000000042_2zz00_2llp600_000_0_0000000000000000000nz0.1vtkv3y", 60.0, 1.0},
            {"watercolor-drag", "watercolor-drag.M60i000000000cg_1ul0000000000_2llp600_000_0_0000000000000000000nz0.1s9xl9o", 120.0, 1.0},
    };
    static final String GOUACHE_LIFT = "gouache-lift.M80bp000000004t_2zz00_177at_000_0_0000000000000000000nz0.13ztx3e";
    static final String SMEAR = "ink-smear.M00hg005d0000at_000zz3a00000000000_3p6jsat_000_0_0000000000000000000nz0.53twbc";

    // ---- pages, marks and strokes ----

    /** The marks page's tone at {@code x, y} (scripts/carry_goldens.cjs mark): two black lines, a grey band, hatching; 255 paper. */
    static int mark(int x, int y) {
        if (x >= 100 && x < 106 || x >= 340 && x < 346) return 0;
        if (x >= 200 && x < 280) return 150;
        if (x >= 380 && x < 460 && Math.floorMod(x + y / 2, 10) < 2) return 90;
        return 255;
    }
    /** A blank one-pair page with {@code tone(x, y)} (255: nothing) laid on {@code layer}. */
    static ToneDocument page(int layer, java.util.function.IntBinaryOperator tone) { return page(layer, 0, tone); }
    /** As above, the marks of {@code kinds}: on that kind's plane ({@link ToneDocument#planeOf}), where a brush of it smudges. */
    static ToneDocument page(int layer, int kinds, java.util.function.IntBinaryOperator tone) {
        ToneDocument d = InkWashLayers.blank(W, H);
        lay(d, layer, kinds, tone);
        return d;
    }
    static void lay(ToneDocument d, int layer, java.util.function.IntBinaryOperator tone) { lay(d, layer, 0, tone); }
    static void lay(ToneDocument d, int layer, int kinds, java.util.function.IntBinaryOperator tone) {
        d.selectLayer(layer);
        d.begin(1, kinds);
        int[] row = new int[d.width];
        for (int y = 0; y < d.height; y++) {
            for (int x = 0; x < d.width; x++) { int t = tone.applyAsInt(x, y); row[x] = t >= 255 ? -1 : t; }
            d.setTones(0, y, d.width, 1, row);
        }
        d.finish();
    }
    static int ownLayer(ModularBrush b) { return ModularSizes.inks(b) ? InkWashLayers.INK : InkWashLayers.WASH; }
    /** The golden wave (carry_goldens.cjs): x, y, pressure travel, ms. */
    static double[][] wave() {
        double[][] o = new double[121][];
        for (int i = 0; i <= 120; i++) { double t = i / 120.0; o[i] = new double[]{40 + 400 * t, 180 + 60 * Math.sin(2 * Math.PI * t), .2 + .7 * Math.sin(Math.PI * t), i * 5}; }
        return o;
    }
    /** A straight stroke from {@code x0} to {@code x1} at {@code y}, pressure travel .8, a sample every 2.5 px. */
    static double[][] across(double x0, double x1, double y) {
        int n = (int)Math.ceil(Math.abs(x1 - x0) / 2.5);
        double[][] o = new double[n + 1][];
        for (int i = 0; i <= n; i++) o[i] = new double[]{x0 + (x1 - x0) * i / n, y, .8, i * 4};
        return o;
    }
    /** {@code b} along {@code path} on the selected pair, live, three samples an event, finished. */
    static void live(ToneDocument d, ModularBrush b, double size, double darkness, WatercolorSession session, double[][] path) {
        feed(ModularStroke.start(d, b, size, darkness, session), path);
    }
    static void feed(DrawingStroke s, double[][] path) {
        for (int i = 0; i < path.length; i++) {
            double[] p = path[i];
            s.sample((float)p[0], (float)p[1], ToolSettings.pressure((float)p[2]), 0, 0, (long)p[3]);
            if (i % 3 == 2) s.present();
        }
        s.present();
        s.finish();
    }
    /** The kinds {@code b} lays: a brush with paint of its own smudges its kind's plane, where the checks lay its marks. */
    static int kinds(ModularBrush b) { return ResolvedBrush.of(b, 20, .5).kind; }
    static ModularBrush brush(String code) { ModularBrush b = ModularCode.decode(code); check(b != null, "decodes: " + code); return b; }
    static ModularBrush with(ModularBrush b, PartChoice life) { ModularBrush c = b.copy(); c.set(BrushPart.LIFE, new ModularBrush.Part(life)); return c; }
    static ModularBrush load(ModularBrush b, double load) { ModularBrush c = b.copy(); if (c.part(BrushPart.CARRY).has(PartDial.LOAD)) c.part(BrushPart.CARRY).set(PartDial.LOAD, load); return c; }
    static int q(int tone) { return Math.round(tone / 17f); }
    /** The mean tone shown in the box {@code x0..x1, y0..y1}. */
    static double mean(ToneDocument d, int x0, int x1, int y0, int y1) {
        double s = 0; int n = 0;
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) { s += d.compositeTone(x, y); n++; }
        return s / n;
    }

    // ---- 1. codes ----

    static void codes() {
        for (PartChoice c : new PartChoice[]{PartChoice.LIFT, PartChoice.DRAG, PartChoice.SMUDGE}) for (double v : new double[]{0, .13, .5, .77, 1}) {
            ModularBrush b = brush(SMEAR);
            ModularBrush.Part p = new ModularBrush.Part(c);
            for (PartDial d : c.dials) p.set(d, v);
            b.set(BrushPart.CARRY, p);
            String code = ModularCode.encode(b.normalize());
            ModularBrush back = ModularCode.decode(code);
            check(back.choice(BrushPart.CARRY) == c && ModularCode.encode(back).equals(code), c + " at " + v + " round-trips: " + code);
            for (PartDial d : c.dials) check(Math.abs(back.part(BrushPart.CARRY).get(d) - v) < .001, c + "'s " + d + " keeps " + v);
        }
        // Codes written by the web lab: Lift, Drag, Smear (the lab's Smudge) decode to its values and re-encode byte for byte.
        for (Object[] g : GOLDENS) check(ModularCode.encode(brush((String)g[1])).equals(g[1]), g[0] + " re-encodes byte for byte");
        for (String c : new String[]{GOUACHE_LIFT, SMEAR, "test-carry.Md0i000000000at_000zz0000000000000_2llp6ul_000_0_0000000000000000000nz0.9ix"})
            check(ModularCode.encode(brush(c)).equals(c), c + " re-encodes byte for byte");
        ModularBrush lift = brush(GOUACHE_LIFT), smear = brush(SMEAR);
        check(lift.choice(BrushPart.CARRY) == PartChoice.LIFT && Math.abs(lift.part(BrushPart.CARRY).get(PartDial.PICK) - .2) < .001 && Math.abs(lift.part(BrushPart.CARRY).get(PartDial.LAY) - .3) < .001
                && smear.choice(BrushPart.CARRY) == PartChoice.SMUDGE && Math.abs(smear.part(BrushPart.CARRY).get(PartDial.LOAD) - .3) < .001, "the lab's defaults decode as written");
        // 2000 rolls with Smudge on round-trip.
        int n = 0;
        for (ModularStyle s : ModularStyle.all()) for (int i = 0; i < 143; i++, n++) {
            ModularBrush b = s.roll(new Random(9000L * (s.index + 1) + i));
            String code = ModularCode.encode(b);
            check(ModularCode.encode(ModularCode.decode(code)).equals(code), "a roll round-trips: " + code);
        }
        check(n >= 2000, "2000 rolls: " + n);
    }

    // ---- 2. golden pages against the web lab ----

    /** The app's drawing of golden {@code g}: its brush along the wave over the marks on its own layer. */
    static ToneDocument golden(Object[] g) {
        ModularBrush b = brush((String)g[1]);
        ToneDocument d = page(ownLayer(b), ResolvedBrush.of(b, (Double)g[2], (Double)g[3]).kind, CarrySmudgeChecks::mark);
        live(d, b, (Double)g[2], (Double)g[3], null, wave());
        return d;
    }
    static BufferedImage lab(String name) throws Exception {
        return javax.imageio.ImageIO.read(new File("app/src/test/resources/carry/" + name + ".png"));
    }
    static void goldens() throws Exception {
        for (Object[] g : GOLDENS) {
            ToneDocument d = golden(g);
            BufferedImage lab = lab((String)g[0]);
            int off = 0, far = 0, changed = 0;
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
                int a = q(d.compositeTone(x, y)), b = q(lab.getRaster().getSample(x, y, 0)), m = q(mark(x, y));
                if (b != m) changed++;
                if (Math.abs(a - b) == 1) off++; else if (a != b) far++;
            }
            note(String.format(Locale.ROOT, "golden %-16s changed %6d, one grey off %5d (%.3f%%), further %d", g[0], changed, off, 100.0 * off / (W * H), far));
            check(changed > 500, g[0] + ": the lab's smudge changes the page: " + changed);
            check(far == 0 && off <= W * H / 1000, g[0] + " draws the lab's page at 16 greys: " + off + " pixels one grey off, " + far + " further");
        }
    }
    /** Each golden beside the app's drawing (lab left, app right, the difference below), 16 greys, into {@code out}. */
    static void pairs(File out) throws Exception {
        out.mkdirs();
        for (Object[] g : GOLDENS) {
            ToneDocument d = golden(g);
            BufferedImage lab = lab((String)g[0]), img = new BufferedImage(2 * W + 8, 2 * H + 8, BufferedImage.TYPE_BYTE_GRAY);
            for (int y = 0; y < 2 * H + 8; y++) for (int x = 0; x < 2 * W + 8; x++) img.getRaster().setSample(x, y, 0, 128);
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
                int a = q(lab.getRaster().getSample(x, y, 0)), b = q(d.compositeTone(x, y));
                img.getRaster().setSample(x, y, 0, a * 17); img.getRaster().setSample(W + 8 + x, y, 0, b * 17);
                img.getRaster().setSample(x, H + 8 + y, 0, a == b ? 255 : 0);
            }
            javax.imageio.ImageIO.write(img, "png", new File(out, "golden-" + g[0] + ".png"));
        }
        System.out.println("wrote " + out);
    }

    // ---- 3. acceptance numbers ----

    static void acceptance() {
        // Smear at its defaults drags a 6 px black line at least one brush width along the stroke: there it is darker
        // than the same stroke without the line by at least a grey.
        ModularBrush smear = with(brush(SMEAR), PartChoice.NONE);
        for (double size : new double[]{12, 30}) {
            ToneDocument lined = page(InkWashLayers.INK, kinds(smear), (x, y) -> x >= 100 && x < 106 ? 0 : 255), bare = InkWashLayers.blank(W, H);
            live(lined, smear, size, 1, null, across(40, 440, 180)); live(bare, smear, size, 1, null, across(40, 440, 180));
            int at = (int)Math.round(106 + size), half = (int)Math.round(106 + size / 2);
            double drag = mean(bare, at - 1, at + 1, 177, 183) - mean(lined, at - 1, at + 1, 177, 183);
            double near = mean(bare, half - 1, half + 1, 177, 183) - mean(lined, half - 1, half + 1, 177, 183);
            note(String.format(Locale.ROOT, "smear %2.0f px: half a width past the line %.1f darker, a width %.1f", size, near, drag));
            // A 6 px line under a finger twice as broad: a grey or more a width on. Under one five times as broad it spreads
            // thinner (what it carries is what the line had): still most of a grey half a width on, half a grey a width on.
            check(size <= 12 ? drag >= 17 : near >= 12 && drag >= 8, "Smear (" + size + " px) drags a black line a brush width along: " + near + ", " + drag);
        }
        // A stump over pencil hatching softens the lines (edge contrast down 40% or more) without darkening the average.
        ModularBrush stump = null;
        for (ToolPresets.Preset p : ToolPresets.all()) if (p.name.equals("Stump")) stump = p.brush().parts;
        ToneDocument hatched = page(InkWashLayers.INK, (x, y) -> Math.floorMod(x, 9) < 3 ? 90 : 255);
        double edgeBefore = edges(hatched), meanBefore = mean(hatched, 100, 380, 172, 188);
        live(hatched, stump, 28, 1, null, across(40, 440, 180));
        double edgeAfter = edges(hatched), meanAfter = mean(hatched, 100, 380, 172, 188);
        note(String.format(Locale.ROOT, "stump: edge contrast %.1f -> %.1f, mean %.1f -> %.1f", edgeBefore, edgeAfter, meanBefore, meanAfter));
        check(edgeAfter <= .6 * edgeBefore && meanAfter >= meanBefore - 2, "A stump softens hatching without darkening it: edges " + edgeBefore + " → " + edgeAfter + ", mean " + meanBefore + " → " + meanAfter);
        // Lift with a light wash brush over a 150 band lightens the track by a grey or more; at a middle grey it lifts
        // two greys more than the same brush without Lift.
        ModularBrush lift = brush(GOLDENS[3][1].toString());
        ToneDocument band = page(InkWashLayers.WASH, kinds(lift), (x, y) -> 150), off = page(InkWashLayers.WASH, kinds(lift), (x, y) -> 150), mid = page(InkWashLayers.WASH, kinds(lift), (x, y) -> 150);
        ModularBrush noLift = lift.copy(); noLift.set(BrushPart.CARRY, new ModularBrush.Part(PartChoice.OFF));
        // The light and middle greys as the spec set them, a wash's glazes before washes reached black (ModularSizes.sinceBlack).
        double lightGrey = ModularSizes.sinceBlack(lift, .2), midGrey = ModularSizes.sinceBlack(lift, .5);
        live(band, lift, 60, lightGrey, null, across(40, 440, 180));
        live(mid, lift, 60, midGrey, null, across(40, 440, 180)); live(off, noLift, 60, midGrey, null, across(40, 440, 180));
        double light = mean(band, 120, 360, 175, 185), lifted = mean(mid, 120, 360, 175, 185), plain = mean(off, 120, 360, 175, 185);
        note(String.format(Locale.ROOT, "lift over 150: %.1f (light brush); %.1f vs %.1f without Lift (mid grey)", light, lifted, plain));
        check(light >= 150 + 17 && lifted >= plain + 34, "Lift lightens a wash: " + light + "; " + lifted + " vs " + plain);
        // Light gouache with Lift over a dark band comes out two greys darker than over white, and recovers within two brush widths.
        ToneDocument dark = page(InkWashLayers.WASH, kinds(brush(GOUACHE_LIFT)), (x, y) -> x >= 150 && x < 250 ? 40 : 255);
        live(dark, brush(GOUACHE_LIFT), 60, .31, null, across(20, 460, 180));
        double white = mean(dark, 60, 120, 176, 184), over = mean(dark, 180, 220, 176, 184), after = mean(dark, 250 + 120, 250 + 130, 176, 184);
        note(String.format(Locale.ROOT, "gouache lift: white %.1f, over dark %.1f, two widths after %.1f", white, over, after));
        check(over <= white - 34 && Math.abs(after - white) < 17, "Light gouache Lift greys over dark and recovers: " + white + ", " + over + ", " + after);
        // Drag (a Watercolor at its defaults) bends a crossing line at least half a brush width along.
        ModularBrush drag = with(brush(GOLDENS[5][1].toString()), PartChoice.NONE);
        drag.part(BrushPart.CARRY).set(PartDial.LOAD, .85);
        ToneDocument crossed = page(InkWashLayers.WASH, kinds(drag), (x, y) -> x >= 100 && x < 106 ? 0 : 255), clean = InkWashLayers.blank(W, H);
        live(crossed, drag, 60, .5, null, across(40, 440, 180)); live(clean, drag, 60, .5, null, across(40, 440, 180));
        // Where the line's darkness went: its middle along the stroke, from the difference the line makes on the track.
        double sum = 0, moment = 0;
        for (int x = 60; x < 300; x++) { double extra = mean(clean, x, x, 170, 190) - mean(crossed, x, x, 170, 190); sum += extra; moment += extra * (x + .5); }
        double moved = moment / sum - 103, bent = mean(clean, 135, 137, 177, 183) - mean(crossed, 135, 137, 177, 183);
        note(String.format(Locale.ROOT, "drag: the line's darkness moved %.1f px along (a 60 px brush); half a width past it %.1f darker", moved, bent));
        check(moved >= 30 && bent >= 6, "Drag bends a crossing line half a brush width along: " + moved + " px, " + bent);
    }
    /** The mean step between neighbours along rows 172–188, x 100–380: how crisp the marks are. */
    static double edges(ToneDocument d) {
        double s = 0; int n = 0;
        for (int y = 172; y <= 188; y++) for (int x = 101; x <= 380; x++) { s += Math.abs(d.compositeTone(x, y) - d.compositeTone(x - 1, y)); n++; }
        return s / n;
    }

    // ---- the quick rule and a finger's preview ----

    static void quickRule() {
        ModularBrush stump = brush(GOLDENS[2][1].toString()), drag = load(brush(GOLDENS[5][1].toString()), .85);
        ResolvedBrush finger = ResolvedBrush.of(stump, 28, 1);
        check(!finger.line && finger.body == null && finger.carry.moves, "A finger (Own paint 0) lays no line and no body: only its smudge draws");
        // A finger has no preview that could show its smudge: it draws live while the Manta keeps up (QuickStroke.CARRY), past the panel's 36 px too.
        check(!QuickStroke.quick(finger) && !QuickStroke.quick(ResolvedBrush.of(stump, 120, 1)) && QuickStroke.quick(ResolvedBrush.of(stump, 200, 1)),
                "a 28 or 120 px stump draws live; past what the Manta keeps up with, a smudge goes quick");
        check(!QuickStroke.quick(ResolvedBrush.of(with(drag, PartChoice.DRYING), 30, 1)) && QuickStroke.quick(ResolvedBrush.of(with(drag, PartChoice.DRYING), 120, 1)),
                "a Slow Drag smudges under its preview: big, it goes quick, unlike a Slow wash alone");
        check(Math.abs(QuickStroke.cost(ResolvedBrush.of(stump, QuickStroke.CARRY, 1)) - 1) < .02, "a finger's CARRY px dab is all the pen's time on the Manta (QuickStroke.CARRY)");
        // Drawn quick with the trail off, a finger shows where it goes: the light wash, on its own layer. With it (the
        // default), the smudge itself, under the pen: over blank paper nothing (TrailChecks: over marks).
        for (boolean trail : new boolean[]{false, true}) {
            boolean was = QuickStroke.trail;
            QuickStroke.trail = trail;
            ToneDocument d = InkWashLayers.blank(W, H);
            QuickStroke q = QuickStroke.quickly(d, stump, 90, 1, null);
            QuickStroke.trail = was;
            for (double[] p : wave()) { q.sample((float)p[0], (float)p[1], ToolSettings.pressure((float)p[2]), 0, 0, (long)p[3]); q.present(); }
            int shown = 0; for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (d.compositeTone(x, y) < 255) shown++;
            check(trail ? shown == 0 : shown > 2000, "A finger drawn quick shows " + (trail ? "nothing over blank paper" : "its footprint") + " while the pen is down: " + shown);
            q.finish();
            for (int y = 0; y < H; y += 3) for (int x = 0; x < W; x += 3) check(d.compositeTone(x, y) == 255, "and over blank paper it leaves nothing");
        }
    }

    // ---- 4. own layer only; 5. no halos ----

    static void ownLayer() {
        for (Object[] g : new Object[][]{GOLDENS[5], GOLDENS[1]}) {
            ModularBrush b = brush((String)g[1]);
            ToneDocument d = InkWashLayers.blank(W, H);
            InkWashLayers.add(d, true);
            for (int layer = 0; layer < 4; layer++) { int k = layer; lay(d, layer, (x, y) -> x >= 60 + 80 * k && x < 120 + 80 * k ? 40 + 30 * k : 255); }
            InkWashLayers.select(d, 0);
            byte[][] before = all(d);
            live(d, b, (Double)g[2], 1, null, wave());
            byte[][] after = all(d);
            int own = ownLayer(b);
            for (int layer = 0; layer < 4; layer++) {
                boolean same = java.util.Arrays.equals(before[2 * layer], after[2 * layer]) && java.util.Arrays.equals(before[2 * layer + 1], after[2 * layer + 1]);
                check(layer == own ? !same : same, g[0] + ": layer " + layer + (layer == own ? " smudged" : " byte for byte as it was"));
            }
        }
    }
    static byte[][] all(ToneDocument d) {
        ToneDocument.Snapshot s = d.layerSnapshot();
        byte[][] out = new byte[2 * s.layers.size()][];
        for (int i = 0; i < s.layers.size(); i++) { ToneDocument.Layer l = s.layers.get(i).flattened(); out[2 * i] = l.tones.clone(); out[2 * i + 1] = l.alpha.clone(); }
        return out;
    }
    static void noHalos() {
        // A dark wash, black lines on the ink layer over it, smeared (Stump and Smear) on the ink layer: never lighter than the wash alone.
        for (String c : new String[]{GOLDENS[2][1].toString(), SMEAR}) {
            ToneDocument d = page(InkWashLayers.WASH, (x, y) -> 60);
            lay(d, InkWashLayers.INK, (x, y) -> Math.floorMod(x, 40) < 6 ? 0 : 255);
            live(d, brush(c), 30, 1, null, wave());
            int[] wash = new int[W * H];
            d.layerDarkness(InkWashLayers.WASH, 0, 0, W, H, wash);
            int worst = 0;
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) worst = Math.max(worst, d.compositeTone(x, y) - (255 - wash[y * W + x]));
            check(worst <= 0, "A smear on the ink layer never lightens the wash below: " + worst + " lighter");
        }
    }

    // ---- 6. the page as the pen found it; 7. Undo; 8. settling first ----

    static void penDownPage() {
        for (Object[] g : GOLDENS) for (PartChoice life : new PartChoice[]{PartChoice.NONE, PartChoice.FAST_WET, PartChoice.DRYING}) {
            ModularBrush b = with(load(brush((String)g[1]), .5), life);
            double size = (Double)g[2], dark = Math.max(.5, (Double)g[3]);
            ToneDocument live = page(ownLayer(b), CarrySmudgeChecks::mark), quick = page(ownLayer(b), CarrySmudgeChecks::mark), once = page(ownLayer(b), CarrySmudgeChecks::mark);
            live(live, b, size, dark, null, wave());
            feed(QuickStroke.quickly(quick, b, size, dark, null), wave());
            ModularStroke s = ModularStroke.start(once, b, size, dark, null);
            for (double[] p : wave()) s.sample((float)p[0], (float)p[1], ToolSettings.pressure((float)p[2]), 0, 0, (long)p[3]);
            s.finish();
            String what = g[0] + " " + life;
            if (life != PartChoice.FAST_WET) check(QuickStrokeChecks.same(quick, live), what + ": drawn quick, every layer as live (it smudged the page as found, not its preview)");
            else check(QuickStrokeChecks.same(quick, once), what + ": drawn quick, every layer as live composed once");
        }
    }
    static void undo() {
        ToneDocument d = page(InkWashLayers.WASH, CarrySmudgeChecks::mark);
        lay(d, InkWashLayers.INK, (x, y) -> Math.floorMod(x, 50) < 4 ? 0 : 255);
        byte[][] before = all(d);
        live(d, brush(GOLDENS[2][1].toString()), 28, 1, null, wave());
        check(d.undo() && ModularStrokeChecks.same(before, all(d)), "One Undo takes a smudge back, every layer byte for byte");
        // A Slow Drag that settles after lift: the stroke and its settle are one Undo.
        WatercolorSession session = new WatercolorSession();
        ModularBrush slow = brush(GOLDENS[5][1].toString());
        ModularBrush.Part life = new ModularBrush.Part(PartChoice.DRYING); life.set(PartDial.SETTLE, .5); slow.set(BrushPart.LIFE, life);
        slow = load(slow, .85);
        live(d, slow, 90, .6, session, wave());
        check(session.wet(), "a Slow Drag keeps settling after lift");
        session.settleNow(d);
        check(d.undo() && ModularStrokeChecks.same(before, all(d)), "One Undo takes a Slow Drag and its settling back");
    }
    static void settlesFirst() {
        ModularBrush wash = with(brush(GOLDENS[5][1].toString()), PartChoice.DRYING);
        wash.set(BrushPart.CARRY, new ModularBrush.Part(PartChoice.OFF));
        wash.part(BrushPart.LIFE).set(PartDial.SETTLE, .5);
        ModularBrush drag = load(brush(GOLDENS[5][1].toString()), .5);
        // As the drawing screen starts a stroke (InkWash.stroke): what still settles laid first.
        ToneDocument a = InkWashLayers.blank(W, H), b = InkWashLayers.blank(W, H);
        WatercolorSession sa = new WatercolorSession(), sb = new WatercolorSession();
        live(a, wash, 90, .7, sa, across(240, 241, 60)); live(b, wash, 90, .7, sb, across(240, 241, 60));
        live(a, wash, 90, .7, sa, new double[][]{{240, 40, .8, 0}, {240, 330, .8, 400}});
        live(b, wash, 90, .7, sb, new double[][]{{240, 40, .8, 0}, {240, 330, .8, 400}});
        check(sa.wet() && sb.wet(), "the wash still settles");
        sa.settleNow(a);
        ToneDocument settled = a;
        sb.settleNow(b);
        live(settled, drag, 70, .5, sa, across(40, 440, 180));
        live(b, drag, 70, .5, sb, across(40, 440, 180));
        check(QuickStrokeChecks.same(settled, b), "A smudge begun while a wash settles smudges it settled");
        // And it never reads the wash unsettled: the plane is filled at the first dab, after the settle.
        ToneDocument raw = InkWashLayers.blank(W, H);
        WatercolorSession sr = new WatercolorSession();
        live(raw, wash, 90, .7, sr, across(240, 241, 60));
        live(raw, wash, 90, .7, sr, new double[][]{{240, 40, .8, 0}, {240, 330, .8, 400}});
        sr.dry("the check reads the wash unsettled");
        live(raw, drag, 70, .5, sr, across(40, 440, 180));
        check(!QuickStrokeChecks.same(raw, b), "(over the wash as it was before settling it would differ)");
    }

    // ---- 9. gravity; 10. the eraser; 11. cores ----

    static void gravity() {
        // A smudge-only stroke (the Stump) on a tilted page draws what it draws on a level one.
        ModularBrush stump = brush(GOLDENS[2][1].toString());
        ToneDocument level = page(InkWashLayers.INK, CarrySmudgeChecks::mark), tilted = page(InkWashLayers.INK, CarrySmudgeChecks::mark);
        WatercolorSession up = new WatercolorSession(), down = new WatercolorSession();
        down.tilt = () -> PageTilt.of(0, 1);
        live(level, stump, 28, 1, up, wave()); live(tilted, stump, 28, 1, down, wave());
        check(QuickStrokeChecks.same(level, tilted), "The page's tilt doesn't move a smudge");
        // A Slow Drag with Runs on a tilted page: only its own wash runs, downhill; the smudge laid above it stays put.
        ModularBrush slow = load(brush(GOLDENS[5][1].toString()), .85);
        ModularBrush.Part life = new ModularBrush.Part(PartChoice.DRYING); life.set(PartDial.SETTLE, .5); life.set(PartDial.RUNS, .8); slow.set(BrushPart.LIFE, life);
        ToneDocument a = page(InkWashLayers.WASH, (x, y) -> x >= 100 && x < 106 ? 0 : 255), b = page(InkWashLayers.WASH, (x, y) -> x >= 100 && x < 106 ? 0 : 255);
        WatercolorSession flat = new WatercolorSession(), steep = new WatercolorSession();
        steep.tilt = () -> PageTilt.of(0, 1);
        live(a, slow, 60, .5, flat, across(40, 440, 180)); live(b, slow, 60, .5, steep, across(40, 440, 180));
        flat.settleNow(a); steep.settleNow(b);
        int uphill = 0;
        for (int y = 0; y < 180 - 40; y++) for (int x = 0; x < W; x++) if (a.compositeTone(x, y) != b.compositeTone(x, y)) uphill++;
        check(uphill == 0, "Uphill of a Slow Drag nothing moves on a tilted page: " + uphill);
    }
    static void eraser() {
        ToneDocument d = page(InkWashLayers.WASH, CarrySmudgeChecks::mark);
        lay(d, InkWashLayers.INK, (x, y) -> Math.floorMod(x, 30) < 4 ? 0 : 255);
        live(d, brush(GOLDENS[1][1].toString()), 30, 1, null, wave());
        live(d, brush(GOLDENS[5][1].toString()), 120, 1, null, wave());
        EraseBothStroke e = new EraseBothStroke(d, 40f);
        for (int x = 20; x <= 460; x += 2) e.sample(x, 180, 1, 0, 0, x);
        e.finish();
        for (int x = 40; x <= 440; x++) check(d.compositeTone(x, 180) == 255, "Erasing over a smudge erases both halves: " + x);
    }
    static void cores() {
        for (Object[] g : new Object[][]{GOLDENS[5], GOLDENS[1]}) for (double size : new double[]{(Double)g[2], 300}) {
            ModularBrush b = load(brush((String)g[1]), .5);
            String[] crc = new String[2];
            for (int i = 0; i < 2; i++) {
                boolean was = Cores.shared;
                Cores.shared = i == 0;
                try { ToneDocument d = page(ownLayer(b), CarrySmudgeChecks::mark); live(d, b, size, 1, null, wave()); crc[i] = LegacyConversionChecks.crc(d); }
                finally { Cores.shared = was; }
            }
            check(crc[0].equals(crc[1]), g[0] + " at " + size + " px draws the same on several cores as on one");
        }
    }

    // ---- 12. rolls; 13. the sweep ----

    /** The CRC of 2100 rolls' codes, each with Smudge set Off, from before Smudge rolled (inkwash-v2-lab f17cecd). */
    static final String ROLLS_BEFORE = "5e4d29f1";
    static void rolls() {
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        // Float, Bristles, Stipple, Hatch, Blend, Halo, Screen, Screentone, Clear Water, Flick, Fling, the Filbert, Paint Load, Fading and Lemon Juice, rolled later still, are off here (FloatChecks, BristleSwarmChecks, StippleChecks, HatchChecks, BlendChecks, HaloChecks and ScreenChecks show they change nothing else), and names keep their adjective (BrushNamesChecks).
        ModularStyle.floatRolls = false; ModularStyle.bristleRolls = false; ModularStyle.patternRolls = false; ModularStyle.blendRolls = false; ModularStyle.haloRolls = false; ModularStyle.coreRolls = false; ModularStyle.screenRolls = false; ModularStyle.toneRolls = false; ModularStyle.waterRolls = false; ModularStyle.flickRolls = false; ModularStyle.flingRolls = false; ModularStyle.oilRolls = false; ModularStyle.fadeRolls = false; ModularStyle.lemonRolls = false; BrushNames.traitNames = false;
        try {
            // The styles from before Print (which rolls a Screen every time).
            for (ModularStyle s : ModularStyle.all().subList(0, 14)) for (int i = 0; i < 150; i++) {
                ModularBrush b = s.roll(new Random(7000L * (s.index + 1) + i));
                b.set(BrushPart.CARRY, new ModularBrush.Part(PartChoice.OFF));
                // Erasable starts at the medium's real value, drawing nothing from the Random: set back to 1, as before it.
                ErasableChecks.allErasable(b);
                crc.update(ModularCode.encode(b).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        } finally { ModularStyle.floatRolls = true; ModularStyle.bristleRolls = true; ModularStyle.patternRolls = true; ModularStyle.blendRolls = true; ModularStyle.haloRolls = true; ModularStyle.coreRolls = true; ModularStyle.screenRolls = true; ModularStyle.toneRolls = true; ModularStyle.waterRolls = true; ModularStyle.flickRolls = true; ModularStyle.flingRolls = true; ModularStyle.oilRolls = true; ModularStyle.fadeRolls = true; ModularStyle.lemonRolls = true; BrushNames.traitNames = true; }
        check(Long.toHexString(crc.getValue()).equals(ROLLS_BEFORE), "Smudge rolls after every part: every other part rolls as before for the same Random (" + Long.toHexString(crc.getValue()) + ")");
        // The lab's weights: Pencil Smear 1 in 7, Charcoal 1 in 4, Pastels 1 in 3, Gouache Lift 1 in 2, Oils Drag 7 in 10, Anything goes each 1 in 9.
        Object[][] want = {{"Pencil", PartChoice.SMUDGE, 1 / 7.0}, {"Charcoal", PartChoice.SMUDGE, .25}, {"Pastels", PartChoice.SMUDGE, 1 / 3.0},
                {"Gouache", PartChoice.LIFT, .5}, {"Oils", PartChoice.DRAG, .7}, {"Anything goes", PartChoice.LIFT, 1 / 9.0}, {"Anything goes", PartChoice.DRAG, 1 / 9.0},
                {"Anything goes", PartChoice.SMUDGE, 1 / 9.0}, {"Fineliner", PartChoice.SMUDGE, 0.0}, {"Watercolor", PartChoice.DRAG, 0.0}};
        Map<String, Map<PartChoice, Integer>> seen = new java.util.HashMap<>();
        Random random = new Random(11);
        for (Object[] w : want) {
            String style = (String)w[0];
            if (seen.containsKey(style)) continue;
            Map<PartChoice, Integer> n = new EnumMap<>(PartChoice.class);
            for (int i = 0; i < 2000; i++) n.merge(ModularStyle.named(style).roll(random).choice(BrushPart.CARRY), 1, Integer::sum);
            seen.put(style, n);
        }
        for (Object[] w : want) {
            double got = seen.get(w[0]).getOrDefault(w[1], 0) / 2000.0, p = (Double)w[2];
            check(Math.abs(got - p) <= 4 * Math.sqrt(p * (1 - p) / 2000) + 1e-9, w[0] + " rolls " + w[1] + " at the lab's weight " + p + ": " + got);
        }
    }
    static void sweep() {
        int n = 0, w = 200, h = 120;
        ModularBrush base = brush(SMEAR);
        for (PartChoice tip : BrushPart.TIP.choices()) for (PartChoice medium : BrushPart.MEDIUM.choices()) for (PartChoice carry : BrushPart.CARRY.choices())
            for (PartChoice life : BrushPart.LIFE.choices()) {
                ModularBrush b = base.copy();
                b.set(BrushPart.TIP, ModularBrush.switchTo(b.part(BrushPart.TIP), tip).normalize());
                b.set(BrushPart.MEDIUM, new ModularBrush.Part(medium));
                b.set(BrushPart.CARRY, new ModularBrush.Part(carry));
                b.set(BrushPart.LIFE, new ModularBrush.Part(life));
                ToneDocument d = InkWashLayers.blank(w, h);
                int layer = ownLayer(b);
                lay(d, layer, (x, y) -> x >= 80 && x < 120 ? 60 : 255);
                byte[][] before = all(d);
                ModularStroke s = ModularStroke.start(d, b, medium == PartChoice.WASH || medium == PartChoice.COVER ? 40 : 10, 1, new WatercolorSession());
                for (int i = 0; i <= 60; i++) s.sample(20 + 160 * i / 60f, (float)(60 + 25 * Math.sin(i / 9.0)), ToolSettings.pressure(.3f + .6f * (float)Math.sin(Math.PI * i / 60)), 0, 0, i * 4L);
                s.present();
                s.finish();
                String what = tip + " " + medium + " " + carry + " " + life;
                // A resist draws on the page's masking fluid (and smudges nothing); Clear Water on the water lays clear rings on a bath with nothing floating.
                check(!ModularStrokeChecks.same(before, all(d)) || medium == PartChoice.RESIST && d.hasMask() || carry == PartChoice.CLEAR_WATER && life == PartChoice.FLOAT, what + " draws");
                check(s.carryMissed() == 0, what + ": the smudge found its page everywhere it went");
                n++;
            }
        check(n == BrushPart.TIP.choices().size() * BrushPart.MEDIUM.choices().size() * BrushPart.CARRY.choices().size() * BrushPart.LIFE.choices().size(), "every tip × medium × smudge × drying (Float too): " + n);
    }

    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
