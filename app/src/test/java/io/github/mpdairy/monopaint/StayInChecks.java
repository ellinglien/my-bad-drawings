package io.github.mpdairy.monopaint;

import java.util.Arrays;
import java.util.Locale;
import java.util.Random;

/**
 * Stay in Lines ({@link PartChoice#STAY_IN}, {@link StayIn}; mischief-lab-2.html section 1): Smudge choice 5 with its
 * Flood (every older code byte for byte), never rolled or swapped to. The flood on Fill's prototype pages at page scale:
 * a ring with a 4 px gap holds it and it fills the ring to the line; a 10° wedge fills to its point; Flood 0 is the brush
 * alone, partway runs about 220 × flood² px past it, and the flood runs out by pen time. A watercolour stroke on a
 * drawing (Instant, Quick and Slow): only the wash layer changes, only inside the shape the stroke began in, the ink
 * lines byte for byte, the settling after lift kept in; a wash-layer line holds nothing; on open paper the stroke is a
 * plain wash's byte for byte; never drawn quick. Its words. And the cost: a sample's flood within 0.1 ms on the desktop
 * (the Manta about 20 times slower), and the shape found at pen-down. Any argument prints each case.
 */
public final class StayInChecks {
    static boolean verbose;
    static void note(String s) { if (verbose) System.out.println(s); }
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
    static double s(double v) { return FillRegionChecks.s(v); }

    public static void main(String[] args) throws Exception {
        verbose = args.length > 0;
        Assets.install(Assets.folder(Assets.FOLDER));
        codes(); ring(); wedge(); reach(); runs();
        String strokes = strokes();
        nothing(); open(); words();
        String busy = busy();
        String cost = cost();
        System.out.println("PASS: Stay in Lines is Smudge choice 5 with Flood (every older code byte for byte), never rolled or swapped to; "
                + "a ring with a 4 px gap holds the flood, filled to the line; a 10° wedge fills to its point; Flood 0 the brush alone, "
                + "partway about 220 × flood² px past it, running out by pen time; " + strokes + "; a wash-layer line holds nothing; on open "
                + "paper a plain wash byte for byte; never quick; its words; " + busy + "; " + cost);
    }

    /** A watercolour (Fill's checks' Slow bloom) staying in the lines at {@code flood}, drying as {@code life}. */
    static ModularBrush stays(double flood, PartChoice life) {
        ModularBrush b = BlendChecks.life(FillChecks.watercolour(), life);
        ModularBrush.Part c = new ModularBrush.Part(PartChoice.STAY_IN);
        c.set(PartDial.FLOOD, flood);
        b.set(BrushPart.CARRY, c);
        return b.normalize();
    }

    // ---- 1. the choice and its code ----

    static void codes() {
        check(PartChoice.STAY_IN.index() == 5 && PartChoice.of(BrushPart.CARRY, 5) == PartChoice.STAY_IN && PartChoice.of(BrushPart.CARRY, 6) == PartChoice.SHAPE
                && PartChoice.STAY_IN.key.equals("stayin") && PartChoice.STAY_IN.label.equals("Stay in Lines"), "Stay in Lines is Smudge choice 5");
        check(PartModelChecks.dials(PartChoice.STAY_IN).equals("flood") && PartChoice.STAY_IN.defaultOf(PartDial.FLOOD) == .35 && PartChoice.STAY_IN.macro == PartDial.FLOOD
                && PartChoice.STAY_IN.words[0].equals("brush") && PartChoice.STAY_IN.words[1].equals("fills") && PartDial.FLOOD.label.equals("Flood"), "its dial: Flood (.35), brush to fills");
        ModularBrush b = stays(.6, PartChoice.DRYING);
        b.name = "lines";
        String code = ModularCode.encode(b);
        ModularBrush back = ModularCode.decode(code);
        check(code.split("_")[2].startsWith("5") && back != null && back.choice(BrushPart.CARRY) == PartChoice.STAY_IN && ModularCode.encode(back).equals(code)
                && Math.abs(back.part(BrushPart.CARRY).get(PartDial.FLOOD) - .6) < 1e-3, "its code round-trips: " + code);
        for (ToolPresets.Preset p : ToolPresets.all()) {
            ModularBrush o = ModularCode.decode(p.code);
            if (o != null) check(ModularCode.encode(o).equals(p.code) || HandHatchChecks.handHatch(p.code), "an older code writes back byte for byte (Hand Hatch's as Hatch): " + p.code);
        }
        for (String c : new String[]{ResistChecks.FLUID, ResistChecks.WAX, ResistChecks.INK, BlendChecks.CHALK})
            check(ModularCode.encode(ModularCode.decode(c)).equals(c), "an older code writes back byte for byte: " + c);
        check(ModularFeatures.seedOnly(PartChoice.STAY_IN) && !ModularFeatures.varies(PartChoice.STAY_IN) && !ModularFeatures.late(PartChoice.STAY_IN), "never a variation's swap");
        for (int i = 0; i < 1400; i++) {
            ModularBrush r = ModularStyle.all().get(i % ModularStyle.all().size()).roll(new Random(i));
            check(r.choice(BrushPart.CARRY) != PartChoice.STAY_IN, "never rolled");
            if (i % 20 == 0) for (ModularVariations.Made m : ModularVariations.make(r, new ModularLocks(), new Random(i)))
                check(m.brush.choice(BrushPart.CARRY) != PartChoice.STAY_IN, "never a variation's swap: " + m.caption);
        }
        // Resolved: no smudge; its Flood; on water nothing.
        ResolvedBrush r = ResolvedBrush.of(b, 60, .55), f = ResolvedBrush.of(stays(.6, PartChoice.FLOAT), 60, .55);
        check(r.carry == null && Math.abs(r.stayIn - .6) < 1e-3 && r.staysIn() && !f.staysIn(), "resolved: no smudge, its Flood; on water none");
        check(!QuickStroke.quick(ResolvedBrush.of(stays(.6, PartChoice.FAST_WET), 400, .55)) && QuickStroke.quick(ResolvedBrush.of(BlendChecks.life(FillChecks.watercolour(), PartChoice.FAST_WET), 600, .55)),
                "never drawn quick (a Quick wash that size is)");
        check(PartDial.FLOOD.shown(0).equals("Brush") && PartDial.FLOOD.shown(1).equals("Fills") && PartDial.FLOOD.shown(.35).equals("4 mm") && PartDial.FLOOD.shown(.7).equals("17 mm"),
                "Flood shown: Brush, 4 mm, 17 mm, Fills: " + PartDial.FLOOD.shown(.35) + ", " + PartDial.FLOOD.shown(.7));
    }

    // ---- 2. the flood on Fill's pages ----

    /** The flood of a tap (a dab of {@code radius} at {@code x, y}) on {@code page} at {@code flood}, run to its end; null for no shape. */
    static StayIn tap(FillRegionChecks.Page page, double x, double y, double radius, double flood) {
        FillRegion.Room room = StayIn.shape(page.w, page.h, page, (int) x, (int) y);
        if (room == null) return null;
        StayIn st = new StayIn(room, flood);
        StrokePoints pts = new StrokePoints();
        pts.add((float) x, (float) y, .8f, 0, 0, 0);
        FloatTiles into = new FloatTiles(page.w, page.h, 0);
        st.sample(pts, 0, radius, .5f, into);
        st.finish(into);
        return st;
    }
    /** The pixels the flood wet, as a region: for {@link FillRegionChecks#dryAndOutside}. */
    static FillRegion.Room wetRoom(StayIn st, int w, int h) {
        int[] b = st.box();
        int bw = b[2] - b[0] + 1, n = 0;
        byte[] at = new byte[bw * (b[3] - b[1] + 1)];
        for (int y = b[1]; y <= b[3]; y++) for (int x = b[0]; x <= b[2]; x++) if (st.wet(x, y) > 0) { at[(y - b[1]) * bw + x - b[0]] = FillRegion.OPEN; n++; }
        return new FillRegion.Room(b[0], b[1], b[2], b[3], at, n);
    }

    /** The prototype's demo ring: radius 120 px, a 4 px gap, at pen .05, .7 and 1. The flood fills it to the line, and stays in. */
    static void ring() {
        for (double p : new double[]{.05, .7, 1}) {
            FillRegionChecks.Page page = new FillRegionChecks.Page(700, 700);
            double R = s(120);
            page.line(FillRegionChecks.arc(350, 350, R, -Math.PI / 2 + s(4) / R, 1.5 * Math.PI), p);
            StayIn st = tap(page, 350, 350, 20, 1);
            check(st != null, "a ring with a 4 px gap at pen " + p + " holds");
            // Outside: wet past the ring's line; dry: any pixel of the shape (paper, margin, the line's edge, pockets) left dry.
            int outside = 0, dry = 0;
            // The line's far edge: its radius, and the antialiased pixel and the layer behind it the wash runs under.
            double line = s(FillRegionChecks.penR(p)) + 2;
            for (int y = 0; y < page.h; y++) for (int x = 0; x < page.w; x++) {
                if (st.wet(x, y) > 0 && Math.hypot(x + .5 - 350, y + .5 - 350) > R + line) outside++;
                if (st.room.inside(x, y) && st.wet(x, y) <= 0) dry++;
            }
            note("ring, 4 px gap, pen " + p + ": " + dry + " dry, " + outside + " outside, " + st.room.area + " px");
            check(outside == 0, "a ring with a 4 px gap at pen " + p + " holds the flood: " + outside + " wet outside");
            check(dry == 0, "Flood 1 fills the ring to the line at pen " + p + ": " + dry + " dry");
        }
    }
    /** A closed V of 10° (FillRegionChecks.vees): a tap in its middle at Flood 1 fills it to its point. */
    static void wedge() {
        for (double past : new double[]{0, 10}) {
            FillRegionChecks.Page page = new FillRegionChecks.Page(800, 700);
            double a = Math.toRadians(5), L = s(300), ax = 100, ay = 350, e = s(past);
            double[] up = {ax + L * Math.cos(a), ay - L * Math.sin(a)}, down = {ax + L * Math.cos(a), ay + L * Math.sin(a)};
            double[] from1 = {ax - e * Math.cos(a), ay + e * Math.sin(a)}, from2 = {ax - e * Math.cos(a), ay - e * Math.sin(a)};
            page.line(FillRegionChecks.poly(from1, up), .7); page.line(FillRegionChecks.poly(from2, down), .7);
            page.line(FillRegionChecks.poly(new double[]{up[0] - s(10), up[1] - s(10)}, new double[]{down[0] - s(10), down[1] + s(10)}), .7);
            int tx = (int) (ax + L * .6), ty = (int) ay;
            StayIn st = tap(page, tx, ty, 10, 1);
            check(st != null, "a 10° wedge holds");
            int[] r = FillRegionChecks.dryAndOutside(page, wetRoom(st, page.w, page.h), tx, ty);
            // The point itself: the wet pixel nearest it along the wedge.
            // The point itself: the wet pixel nearest it along the wedge, and the first paper there (where the two lines part).
            int tip = Integer.MAX_VALUE, paper = Integer.MAX_VALUE;
            for (int y = 330; y < 370; y++) for (int x = (int) ax; x < 400; x++) {
                if (Math.abs(y + .5 - ay) > (x + .5 - ax) * Math.tan(a)) continue;
                if (st.wet(x, y) > 0) tip = Math.min(tip, x);
                if (page.dark[y * page.w + x] <= FillRegionChecks.PAPERISH) paper = Math.min(paper, x);
            }
            note(String.format(Locale.ROOT, "10° wedge past %.0f: %d dry, %d outside, wet from x = %d, paper from x = %d (the lines meet at %.0f)", past, r[0], r[1], tip, paper, ax));
            check(r[1] == 0 && r[0] == 0, "a 10° wedge (lines " + past + " px past) fills to its point: " + r[0] + " dry, " + r[1] + " outside");
            check(tip <= paper, "the wash reaches the wedge's point: wet from x = " + tip + ", paper from x = " + paper);
        }
    }
    /** A box: Flood 0 wets only round the brush; partway about 220 × flood² prototype px past it (and on to a line within a few px); 1 the whole box. */
    static void reach() {
        FillRegionChecks.Page page = new FillRegionChecks.Page(1400, 900);
        page.line(FillRegionChecks.poly(new double[]{100, 100}, new double[]{1300, 100}, new double[]{1300, 800}, new double[]{100, 800}, new double[]{100, 98}), .7);
        double radius = 20;
        for (double flood : new double[]{0, .35, .7, 1}) {
            StayIn st = tap(page, 700, 450, radius, flood);
            check(st != null, "the box holds");
            double far = 0;
            int wet = 0, open = 0;
            for (int y = 101; y < 800; y++) for (int x = 101; x < 1300; x++) {
                if (st.room.kind(x, y) == FillRegion.OPEN) open++;
                if (st.wet(x, y) <= 0) continue;
                wet++;
                far = Math.max(far, Math.hypot(x + .5 - 700, y + .5 - 450));
            }
            double past = far - radius, want = 220 * flood * flood * FillRegion.SCALE;
            note(String.format(Locale.ROOT, "flood %.2f: wet to %.0f px past the brush (%.0f wanted), %d of %d open px", flood, past, want, wet, open));
            if (flood == 0) check(past <= 2, "Flood 0 is the brush alone: wet " + past + " px past it");
            else if (flood < 1) check(Math.abs(past - want) <= Math.max(4, .1 * want), String.format(Locale.ROOT, "Flood %.2f runs about %.0f px past the brush: %.0f", flood, want, past));
            else check(wet >= open, "Flood 1 fills the box: " + wet + " of " + open);
        }
    }
    /** The flood runs out by pen time, the prototype's 3 px a frame (about 340 page px a second), and only so far each sample. */
    static void runs() {
        FillRegionChecks.Page page = new FillRegionChecks.Page(1400, 900);
        page.line(FillRegionChecks.poly(new double[]{100, 100}, new double[]{1300, 100}, new double[]{1300, 800}, new double[]{100, 800}, new double[]{100, 98}), .7);
        StayIn st = new StayIn(StayIn.shape(page.w, page.h, page, 700, 450), 1);
        StrokePoints pts = new StrokePoints();
        FloatTiles into = new FloatTiles(page.w, page.h, 0);
        double[] at = new double[3];
        long[] when = {0, 300, 600};
        int k = 0;
        for (long t = 0; t <= 600; t += 4) {
            pts.add(700, 450, .8f, t, 0, 0);
            st.sample(pts, t, 10, .5f, into);
            if (k < 3 && t == when[k]) {
                double far = 0;
                for (int y = 101; y < 800; y += 2) for (int x = 101; x < 1300; x += 2) if (st.wet(x, y) > 0) far = Math.max(far, Math.hypot(x - 700, y - 450));
                at[k++] = far;
            }
        }
        double rate = (at[2] - at[1]) / .3;
        note(String.format(Locale.ROOT, "the flood at 0, 300, 600 ms: %.0f, %.0f, %.0f px from the brush's centre (%.0f px a second)", at[0], at[1], at[2], rate));
        check(at[0] < 20 && at[1] > at[0] + 60 && rate > 250 && rate < 420, "the flood runs out visibly, about 340 px a second: " + Arrays.toString(at));
    }

    // ---- 3. strokes on a drawing ----

    /** A scribble from inside the ring across its line and out, pressure travel .8, 4 ms a sample. */
    static double[][] scribble() {
        double[][] o = new double[200][];
        for (int i = 0; i < o.length; i++) { double t = i / 199.0; o[i] = new double[]{560 + 250 * t, 320 + 90 * Math.sin(8 * Math.PI * t), .8, i * 4}; }
        return o;
    }
    static String strokes() {
        StringBuilder out = new StringBuilder();
        for (PartChoice life : new PartChoice[]{PartChoice.NONE, PartChoice.FAST_WET, PartChoice.DRYING})
            for (double flood : new double[]{0, .5, 1}) {
                ToneDocument d = FillChecks.page();
                int ink = FillTool.walls(d), wash = InkWashLayers.washOf(d, 0);
                byte[][] inkBefore = ErasableChecks.layer(d, ink), washBefore = ErasableChecks.layer(d, wash);
                int[] before = FillChecks.shown(d);
                FillRegion.Room room = FillTool.region(d, FillChecks.RING[0], FillChecks.RING[1]);
                // With a session, as on the drawing screen: the shape is found off the pen's thread.
                WatercolorSession session = new WatercolorSession();
                ModularStroke st = ModularStroke.start(d, stays(flood, life), 60, .55, session);
                CarrySmudgeChecks.feed(st, scribble());
                check(st.staysIn(), "the shape is taken in by the lift");
                session.settleNow(d);
                int[] after = FillChecks.shown(d);
                int outside = 0, darker = 0, open = 0, dry = 0;
                for (int y = 0; y < FillChecks.H; y++) for (int x = 0; x < FillChecks.W; x++) {
                    int i = y * FillChecks.W + x;
                    if (after[i] != before[i] && !room.inside(x, y)) outside++;
                    if (after[i] < before[i]) darker++;
                    if (room.kind(x, y) == FillRegion.OPEN) { open++; if (after[i] >= before[i]) dry++; }
                }
                String name = life + " at Flood " + flood;
                check(ErasableChecks.same(ErasableChecks.layer(d, ink), inkBefore), name + ": the ink lines byte for byte");
                check(!ErasableChecks.same(ErasableChecks.layer(d, wash), washBefore), name + ": the wash layer takes it");
                check(outside == 0, name + ": nothing outside the ring, the stroke crossing its line: " + outside);
                if (flood == 1) check(dry == 0, name + ": Fills fills the ring: " + dry + " of " + open + " dry");
                if (flood == 0) check(dry > open / 3, name + ": Flood 0 leaves the ring's far side dry: " + dry + " of " + open);
                // Undo takes it all.
                check(d.undo() && Arrays.equals(FillChecks.shown(d), before), name + ": one Undo step");
                note(String.format(Locale.ROOT, "%s: %d darker, %d of %d dry", name, darker, dry, open));
                if (flood == .5) out.append(out.length() > 0 ? ", " : "").append(String.format(Locale.ROOT, "%s %d of %d px", life.label, open - dry, open));
            }
        return "a watercolour scribble across a ring's line stays in it, Instant, Quick and Slow, the ink lines byte for byte, one Undo step (Flood .5 wets " + out + ")";
    }
    /** A box in ink with a wash-layer line across it: the wash runs over the wash line into the other half. */
    static void nothing() {
        ToneDocument b = InkWashLayers.blank(FillChecks.W, FillChecks.H);
        InkWashLayers.select(b, 0);
        BlendChecks.live(b, BlendChecks.brush(FillChecks.PEN), 7, 1, null, FillChecks.box(200, 150, 700, 500));
        BlendChecks.live(b, BlendChecks.life(FillChecks.watercolour(), PartChoice.NONE), 20, 1, null, BlendChecks.across(190, 710, 325));
        int[] before = FillChecks.shown(b);
        CarrySmudgeChecks.feed(ModularStroke.start(b, stays(1, PartChoice.NONE), 40, .55, null), BlendChecks.across(300, 320, 240));
        int[] after = FillChecks.shown(b);
        int below = 0, outside = 0;
        for (int y = 0; y < FillChecks.H; y++) for (int x = 0; x < FillChecks.W; x++) {
            int i = y * FillChecks.W + x;
            if (y > 340 && y < 490 && x > 210 && x < 690 && after[i] < before[i]) below++;
            if ((x < 195 || x > 705 || y < 145 || y > 505) && after[i] != before[i]) outside++;
        }
        check(below > 50000 && outside == 0, "a line on the wash layer holds nothing: the flood runs past it (" + below + " px), and no farther than the ink box (" + outside + ")");
    }
    /** On open paper the stroke is a plain wash: the page byte for byte as with no smudge. */
    static void open() {
        for (PartChoice life : new PartChoice[]{PartChoice.NONE, PartChoice.FAST_WET, PartChoice.DRYING}) {
            ToneDocument a = FillChecks.page(), b = FillChecks.page();
            double[][] path = BlendChecks.across(20, 120, 40);
            ModularBrush plain = BlendChecks.life(FillChecks.watercolour(), life);
            plain.set(BrushPart.CARRY, new ModularBrush.Part(PartChoice.OFF));
            BlendChecks.live(a, plain, 60, .55, null, path);
            ModularStroke st = ModularStroke.start(b, stays(1, life), 60, .55, null);
            CarrySmudgeChecks.feed(st, path);
            check(!st.staysIn(), life + ": open paper has no shape");
            check(Arrays.deepEquals(BlendChecks.all(a), BlendChecks.all(b)), life + ": on open paper a plain wash, byte for byte");
        }
    }

    // ---- 3b. a busy handwritten page ----

    /**
     * Fill in (the Basic tool in the paint bucket's place, Stay in Lines at Flood 1) on a Manta page of handwriting, a
     * row of looped letters every 95 px with a circle, a box and a small ring drawn over it (the owner, 2026-10-23: the
     * bucket "didn't respond" on such a page, its tap's area the paper round the writing, refused unheld after a
     * long search). Scribbled inside a closed shape it fills that shape to its lines and nothing past them; started in a
     * gap between letters, between rows or in the margin it finds no shape (the search stops {@link StayIn#MOST} px out)
     * and is a plain wash where the pen went: never a refusal, never the page.
     */
    static String busy() {
        int w = 1920, h = 2400;
        ToneDocument page = InkWashLayers.blank(w, h);
        InkWashLayers.select(page, 0);
        ModularBrush pen = BlendChecks.brush(FillChecks.PEN);
        Random rnd = new Random(7);
        for (double y = 260; y < 2250; y += 95)
            for (double x = 120; x < 1700; ) {
                java.util.List<double[]> word = new java.util.ArrayList<>();
                long t = 0;
                for (int k = 2 + rnd.nextInt(7); k > 0; k--) {
                    double lw = 18 + rnd.nextDouble() * 20, lh = 22 + rnd.nextDouble() * 22, open = rnd.nextBoolean() ? 0 : .6;
                    for (double a = 0; a < 2 * Math.PI - open + .25; a += .25)
                        word.add(new double[]{x + lw / 2 - lw / 2 * Math.cos(a), y - lh / 2 - lh / 2 * Math.sin(a), .6, t += 8});
                    x += lw + 4;
                }
                BlendChecks.live(page, pen, 4, 1, null, word.toArray(new double[0][]));
                x += 30 + rnd.nextDouble() * 30;
            }
        BlendChecks.live(page, pen, 6, 1, null, FillChecks.arc(500, 700, 150, 0, 2 * Math.PI + .1));
        BlendChecks.live(page, pen, 6, 1, null, FillChecks.box(1100, 1300, 1500, 1600));
        BlendChecks.live(page, pen, 6, 1, null, FillChecks.arc(1500, 520, 40, 0, 2 * Math.PI + .1));
        ToolPresets.Preset fillIn = null;
        for (ToolPresets.Preset p : ToolPresets.all()) if (p.name.equals("Fill in")) fillIn = p;
        check(fillIn != null && fillIn.brush().parts.choice(BrushPart.CARRY) == PartChoice.STAY_IN && fillIn.brush().parts.part(BrushPart.CARRY).get(PartDial.FLOOD) == 1,
                "Fill in is a Basic tool: Stay in Lines at Flood 1");
        int ink = FillTool.walls(page);
        // A gap between two letters of a row, and a point in the box clear of the writing.
        int[] row = new int[w], gap = null;
        page.layerDarkness(ink, 0, 1185, w, 1, row);
        for (int x = 300; x < 1600 && gap == null; x++) if (row[x] > FillRegion.LINE) {
            int r = x; while (r < w - 1 && row[r + 1] <= FillRegion.LINE) r++;
            if (r - x >= 6 && r - x <= 14) gap = new int[]{(x + r) / 2, 1185};
            x = r;
        }
        check(gap != null, "a gap between letters");
        Object[][] cases = {{"circle", 500, 700, true}, {"box", 1300, 1450, true}, {"ring", 1500, 520, true}, {"gap between letters", gap[0], gap[1], false},
                {"gap between rows", 960, 1210, false}, {"margin", 60, 1200, false}};
        StringBuilder out = new StringBuilder();
        byte[] packed;
        try { packed = DrawingBook.encode(page); } catch (java.io.IOException e) { throw new AssertionError(e); }
        for (Object[] c : cases) {
            int x = (Integer) c[1], y = (Integer) c[2];
            boolean closed = (Boolean) c[3];
            ToneDocument d;
            try { d = DocumentCodec.read(new java.io.ByteArrayInputStream(packed)); } catch (java.io.IOException e) { throw new AssertionError(e); }
            InkWashLayers.select(d, 0);
            int x0 = Math.max(0, x - 800), y0 = Math.max(0, y - 800), x1 = Math.min(w, x + 800), y1 = Math.min(h, y + 800);
            int[] before = new int[(x1 - x0) * (y1 - y0)];
            for (int yy = y0, i = 0; yy < y1; yy++) for (int xx = x0; xx < x1; xx++, i++) before[i] = d.compositeTone(xx, yy);
            FillRegion.Room shape = StayIn.shape(w, h, (bx, by, bw, bh, o) -> d.layerDarkness(ink, bx, by, bw, bh, o), x, y);
            WatercolorSession session = new WatercolorSession();
            ModularStroke st = ModularStroke.start(d, fillIn.brush().parts, 30, .55, session);
            double[][] scribble = new double[60][];
            for (int i = 0; i < 60; i++) scribble[i] = new double[]{x + 12 * Math.cos(i * .7) * (i % 7) / 7, y + 12 * Math.sin(i * .9) * (i % 5) / 5, .6, 100_000 + i * 8L};
            CarrySmudgeChecks.feed(st, scribble);
            session.settleNow(d);
            int changed = 0, outside = 0, open = 0, dry = 0;
            for (int yy = y0, i = 0; yy < y1; yy++) for (int xx = x0; xx < x1; xx++, i++) {
                boolean now = d.compositeTone(xx, yy) != before[i];
                if (now) changed++;
                if (shape != null && now && !shape.inside(xx, yy)) outside++;
                if (shape != null && shape.kind(xx, yy) == FillRegion.OPEN) { open++; if (!now) dry++; }
            }
            String name = "Fill in on a busy page, " + c[0];
            check(st.staysIn() == closed && (shape != null) == closed, name + ": " + (closed ? "finds the shape" : "no shape, a plain wash"));
            if (closed) check(outside == 0 && dry < open / 50, name + ": fills it (" + dry + " of " + open + " dry), nothing past its lines (" + outside + ")");
            else check(changed > 0 && changed < 5000, name + ": a plain wash where the pen went, " + changed + " px");
            note(name + ": " + changed + " px");
            if (closed) out.append(out.length() > 0 ? ", " : "").append(c[0]).append(' ').append(open - dry).append(" px");
        }
        return "Fill in on a busy handwritten page fills a closed shape among the writing (" + out + ") and is a plain wash between letters, rows and in the margin";
    }

    // ---- 4. words ----

    static void words() {
        ModularBrush b = stays(.5, PartChoice.FAST_WET), none = stays(0, PartChoice.FAST_WET), all = stays(1, PartChoice.FAST_WET);
        String said = ModularWords.plainWords(b);
        note(said + " / " + ModularWords.plainWords(none) + " / " + ModularWords.plainWords(all) + " / " + ModularWords.caption(b));
        check(said.contains("Floods inside ink lines.") && ModularWords.plainWords(none).contains("Stays inside ink lines.") && ModularWords.plainWords(all).contains("Fills the shape it starts in."),
                "This brush: " + said);
        check(ModularWords.choiceWords(b, BrushPart.CARRY).equals("Stay in") && ModularWords.caption(b).contains("stay in lines"), "the Smudge card says Stay in, captions tag stay in lines: " + ModularWords.caption(b));
        check(said.length() < ModularWords.MOST && !said.toLowerCase(Locale.ROOT).contains("spread"), "short, no engine words");
    }

    // ---- 5. the cost ----

    /**
     * A Manta-sized page (1920 × 2400) with a box of 1200 × 1200 px drawn in ink and scribbles inside: the shape found
     * at pen-down (off the pen's thread on the drawing screen), and a stroke's flood a sample (Fills, a 60 px brush, 4 ms
     * a sample), the desktop's median; the Manta about 20 times slower (ModularBenchChecks). Over budget, timed again
     * (a busy machine), twice at most.
     */
    static String cost() {
        String line = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try { return timed(); } catch (AssertionError e) { line = e.getMessage(); note("again: " + line); }
        }
        throw new AssertionError(line);
    }
    static String timed() {
        FillRegionChecks.Page page = new FillRegionChecks.Page(1920, 2400);
        page.line(FillRegionChecks.poly(new double[]{360, 600}, new double[]{1560, 600}, new double[]{1560, 1800}, new double[]{360, 1800}, new double[]{360, 598}), .7);
        Random rnd = new Random(3);
        for (int k = 0; k < 12; k++) {
            double cx = 500 + rnd.nextDouble() * 900, cy = 750 + rnd.nextDouble() * 900, r = 30 + rnd.nextDouble() * 60;
            page.line(FillRegionChecks.arc(cx, cy, r, 0, 2 * Math.PI - .3), .5);
        }
        double[] down = new double[9], per = new double[9], worst = new double[9];
        int n = 300;
        for (int run = -3; run < 9; run++) {
            long t0 = System.nanoTime();
            StayIn st = new StayIn(StayIn.shape(page.w, page.h, page, 960, 1200), 1);
            long t1 = System.nanoTime();
            StrokePoints pts = new StrokePoints();
            FloatTiles into = new FloatTiles(page.w, page.h, 0);
            long[] each = new long[n];
            for (int i = 0; i < n; i++) {
                double t = i / (double) n;
                pts.add((float) (700 + 500 * t), (float) (1200 + 200 * Math.sin(5 * t)), .8f, i * 4L, 0, 0);
                long a = System.nanoTime();
                st.sample(pts, i * 4L, 27, .5f, into);
                each[i] = System.nanoTime() - a;
            }
            if (run < 0) continue;
            down[run] = (t1 - t0) / 1e6;
            long sum = 0;
            for (long e : each) sum += e;
            per[run] = sum / 1e6 / n;
            Arrays.sort(each);
            worst[run] = each[(int) (n * .95)] / 1e6;
        }
        Arrays.sort(down); Arrays.sort(per); Arrays.sort(worst);
        double d = down[4], p = per[4], w = worst[4];
        String line = String.format(Locale.ROOT, "on a 1920 × 2400 page, a 1200 px box with drawing in it: the shape found off the pen's thread in %.1f ms, the flood %.3f ms a sample (95th percentile %.3f), desktop medians (the Manta about 20×)", d, p, w);
        note(line);
        Timing.limit(p <= .1, "the flood within 0.1 ms a sample on the desktop: " + line);
        Timing.limit(d <= 160, "the shape at pen-down within Fill's budget for a box that size: " + line);
        return line;
    }

    private StayInChecks() {}
}
