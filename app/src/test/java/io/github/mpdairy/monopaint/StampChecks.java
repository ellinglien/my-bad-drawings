package io.github.mpdairy.monopaint;

import java.util.HashMap;
import java.util.Map;

/**
 * Stamp (mischief-lab-2.html's second pad; {@link StampNib}, {@link StampStamper}): a lasso cut keeps the ink inside it,
 * trimmed, and nothing to cut is none; prints land every Spacing along the line (a tap prints once); Ink Fade None keeps
 * every print as dark as the first, and on it fades; a rest of over a second inks the stamp again, a shorter one carries
 * the ink over; codes round-trip with the nib in the store, and a code without its nib prints round; every other code is
 * byte for byte; never rolled; words.
 */
public final class StampChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        cut(); spacing(); tap(); fade(); rests(); codes(); fallback(); words(); media(); quick();
        System.out.println("PASS: a Stamp's lasso, in marching ants, cuts the ink inside, trimmed (nothing inside: none), Brush Lab's an Undo step;its prints land every Spacing along the line and a tap prints once; "
                + "Ink Fade None keeps every print as dark as the first, on it fades print by print; a rest of over a second inks it afresh, a shorter one carries the ink over; "
                + "drawn quick it ends as live; a code round-trips with its nib in the store, a code without its nib prints round, and every other code is byte for byte; never rolled; its words; ink, wash and Print Shading lay it");
    }

    static final int W = 700, H = 400;

    /** A Stamp brush: ink, Instant, steady, {@code spacing}, {@code fade}, Turns {@code turns}. */
    static ModularBrush stamp(double spacing, double fade, boolean turns, PartChoice medium) {
        ModularBrush b = StippleChecks.mk(PartChoice.STAMP, PartChoice.STEADY, PartDial.STAMP_SPACING, spacing, PartDial.INK_FADE, fade, PartDial.HEAD_TURN, turns ? 0 : 1);
        b.set(BrushPart.MEDIUM, new ModularBrush.Part(medium));
        b.set(BrushPart.LIFE, new ModularBrush.Part(PartChoice.NONE));
        return b.normalize();
    }
    /** A page of tones: ink (tone 0) in the box {@code x0..x1, y0..y1}, the rest paper. */
    static Mirrors.Tones page(int[]... boxes) {
        return (x, y) -> {
            for (int[] b : boxes) if (x >= b[0] && x <= b[2] && y >= b[1] && y <= b[3]) return 0;
            return 255;
        };
    }

    static void cut() {
        // A 20 × 14 block inside the lasso, another block outside it.
        Mirrors.Tones p = page(new int[]{50, 30, 69, 43}, new int[]{200, 30, 220, 50});
        float[] xs = {40, 90, 90, 40}, ys = {20, 20, 60, 60};
        StampNib nib = StampNib.cut(p, 255, xs, ys, 4, W, H);
        check(nib != null && nib.width == 20 && nib.height == 14 && nib.at(0, 0) == 1 && nib.at(19, 13) == 1 && nib.at(10, 7) == 1, "the cut is the ink inside, trimmed: " + (nib == null ? "none" : nib.width + " × " + nib.height));
        // A lasso round half of the block (a triangle) keeps only what is inside it.
        StampNib half = StampNib.cut(p, 255, new float[]{45, 75, 45}, new float[]{25, 25, 55}, 3, W, H);
        check(half != null && half.width <= 20 && half.at(0, 0) == 1 && half.at(half.width - 1, half.height - 1) == 0, "a lasso keeps only the ink inside it");
        check(StampNib.cut(p, 255, new float[]{300, 340, 340, 300}, new float[]{100, 100, 140, 140}, 4, W, H) == null, "nothing inside: nothing to cut");
        check(StampNib.cut(p, 255, xs, ys, 2, W, H) == null, "two points are no lasso");
        // A shaded page's paper isn't ink.
        StampNib shaded = StampNib.cut((x, y) -> x >= 50 && x <= 69 && y >= 30 && y <= 43 ? 0 : 230, 230, xs, ys, 4, W, H);
        check(shaded != null && shaded.width == 20 && shaded.height == 14, "a shaded page's paper is no ink");
        // A big cut is kept at most MOST on its longer side.
        StampNib big = StampNib.cut(page(new int[]{10, 10, 609, 309}), 255, new float[]{0, 650, 650, 0}, new float[]{0, 0, 350, 350}, 4, W, H);
        check(big != null && big.longer() == StampNib.MOST && Math.abs(big.width / (double) big.height - 2) < .02, "a big cut is scaled to " + StampNib.MOST + ": " + big.width + " × " + big.height);
        // The lasso shows as marching ants (MirrorShow's placing stroke, dashed in ink), over the page and never in it.
        MirrorShow show = new MirrorShow();
        show.page(W, H);
        MirrorShow.Place lasso = new MirrorShow.Place(show, true);
        for (int x = 20; x <= 220; x += 4) lasso.sample(x, 100, .5f, 0, 0);
        int[] px = new int[W * H];
        java.util.Arrays.fill(px, 0xffffffff);
        show.draw(px, 0, 0, W, H, false, (x, y) -> 255, 255);
        int dark = 0, runs = 0;
        boolean was = false;
        for (int x = 0; x < W; x++) { boolean on = (px[100 * W + x] & 0xff) < 128; if (on) dark++; if (on && !was) runs++; was = on; }
        check(lasso.lasso && !new MirrorShow.Place(show).lasso && lasso.count() == 51 && runs >= 7 && dark > 80 && dark < 130,
                "the lasso shows as marching ants: " + runs + " dashes, " + dark + " px of 200");
        // Written and read back, the same pixels and id.
        StampNib back = StampNib.read(nib.write());
        check(back != null && back.id == nib.id && back.width == nib.width, "a nib reads back as written");
        check(StampNib.read("1,2,2,AAAA") == null && StampNib.read("nonsense") == null && StampNib.read(null) == null, "anything else reads as no nib");
    }

    /** The prints a stroke along {@code path} lays, and the stamper's state after. */
    static StampStamper.State prints(ModularBrush b, double size, double[][] path, StampStamper.Ink start, FloatTiles into) {
        ResolvedBrush r = ResolvedBrush.of(b, size, 1);
        StrokePoints pts = new StrokePoints();
        LineStamper.Meta meta = new LineStamper.Meta();
        for (double[] p : path) {
            pts.add((float) p[0], (float) p[1], (float) p[2], (long) p[3], Float.NaN, Float.NaN);
            StampStamper.stamp(into, null, r.pattern, pts, r.pattern.size, Math.max(1, pts.count - 1), meta, start);
        }
        return meta.stamp;
    }
    static double[][] line(double x0, double x1, double y, long t0) {
        int n = (int) Math.ceil((x1 - x0) / 3);
        double[][] o = new double[n + 1][];
        for (int i = 0; i <= n; i++) o[i] = new double[]{x0 + (x1 - x0) * i / n, y, .7, t0 + 2 * i};
        return o;
    }

    static void spacing() {
        // Apart (Spacing 120%): a print every 1.2 sizes, the first at the touch-down.
        ModularBrush apart = stamp(1.2, .22, true, PartChoice.INK);
        FloatTiles t = new FloatTiles(W, H, 0);
        StampStamper.State s = prints(apart, 40, line(30, 630, 200, 1000), StampStamper.Ink.FRESH, t);
        ResolvedBrush r = ResolvedBrush.of(apart, 40, 1);
        double gap = StampStamper.gap(r.pattern, r.pattern.size);
        check(gap == 48 && s.prints == 1 + (int) Math.floor(600 / gap), "prints every Spacing: " + s.prints + " at " + gap + " px");
        // Between two prints the paper shows; at each print, ink.
        double at = 0, between = 0;
        for (int k = 0; k < s.prints; k++) at += t.get(30 + (int) Math.round(k * gap), 200);
        for (int k = 0; k + 1 < s.prints; k++) between += t.get(30 + (int) Math.round((k + .5) * gap), 200);
        check(at > 2 && between == 0, "separate prints: ink at each (" + at + "), none between");
        // Close (12%): a continuous nib trail.
        FloatTiles trail = new FloatTiles(W, H, 0);
        StampStamper.State c = prints(stamp(.12, 0, true, PartChoice.INK), 40, line(30, 630, 200, 1000), StampStamper.Ink.FRESH, trail);
        int gaps = 0;
        for (int x = 40; x < 620; x++) if (trail.get(x, 200) == 0) gaps++;
        check(c.prints > 100 && gaps < 6, "a small Spacing is a nib trail: " + c.prints + " prints, " + gaps + " px of paper along it");
        // The same samples batched otherwise lay the same prints.
        FloatTiles once = new FloatTiles(W, H, 0);
        ResolvedBrush rr = ResolvedBrush.of(apart, 40, 1);
        StrokePoints pts = new StrokePoints();
        for (double[] p : line(30, 630, 200, 1000)) pts.add((float) p[0], (float) p[1], (float) p[2], (long) p[3], Float.NaN, Float.NaN);
        StampStamper.stamp(once, null, rr.pattern, pts, rr.pattern.size, 1, new LineStamper.Meta(), StampStamper.Ink.FRESH);
        boolean same = true;
        for (int y = 150; y < 250; y++) for (int x = 0; x < W; x++) same &= once.get(x, y) == t.get(x, y);
        check(same, "however the samples are batched, the same prints");
    }

    static void tap() {
        ToneDocument d = InkWashLayers.blank(W, H);
        DrawingStroke s = ModularStroke.start(d, stamp(.12, .22, true, PartChoice.INK), 60, 1, null);
        CarrySmudgeChecks.feed(s, new double[][]{{300, 200, .7, 1000}});
        int ink = 0, x0 = W, x1 = 0;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (d.compositeTone(x, y) < 250) { ink++; x0 = Math.min(x0, x); x1 = Math.max(x1, x); }
        check(ink > 300 && x1 - x0 <= 64, "a tap prints once, the nib's size: " + ink + " px, " + (x1 - x0 + 1) + " wide");
    }

    /** Each print's ink: the summed coverage within half a gap of each print's centre. */
    static double[] each(FloatTiles t, int n, double gap) {
        double[] out = new double[n];
        for (int k = 0; k < n; k++) {
            int cx = 30 + (int) Math.round(k * gap);
            for (int y = 150; y < 250; y++) for (int x = cx - (int) (gap / 2) + 1; x < cx + gap / 2; x++) out[k] += t.get(Math.max(0, x), y);
        }
        return out;
    }
    static void fade() {
        FloatTiles none = new FloatTiles(W, H, 0), on = new FloatTiles(W, H, 0);
        StampStamper.State a = prints(stamp(1.2, 0, true, PartChoice.INK), 40, line(30, 630, 200, 1000), StampStamper.Ink.FRESH, none);
        StampStamper.State b = prints(stamp(1.2, .22, true, PartChoice.INK), 40, line(30, 630, 200, 1000), StampStamper.Ink.FRESH, on);
        double[] n = each(none, a.prints, 48), f = each(on, b.prints, 48);
        double lo = Double.MAX_VALUE, hi = 0;
        for (double v : n) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
        check(a.load == 1 && lo > .9 * hi, "Ink Fade None: every print as dark as the first (" + Math.round(lo) + " to " + Math.round(hi) + "), ink left " + a.load);
        check(f[0] > 1.5 * f[3] && f[3] > f[6] * .99 && b.load < .5, "Ink Fade on: each print lighter (" + Math.round(f[0]) + ", " + Math.round(f[3]) + ", " + Math.round(f[6]) + ")");
        // The floor: it never runs quite dry.
        check(b.load >= StampStamper.FLOOR, "never under the floor: " + b.load);
        // Pressure prints darker.
        FloatTiles light = new FloatTiles(W, H, 0), hard = new FloatTiles(W, H, 0);
        double[][] soft = line(30, 630, 200, 1000), firm = line(30, 630, 200, 1000);
        for (double[] p : soft) p[2] = .2;
        for (double[] p : firm) p[2] = 1;
        prints(stamp(1.2, 0, true, PartChoice.INK), 40, soft, StampStamper.Ink.FRESH, light);
        prints(stamp(1.2, 0, true, PartChoice.INK), 40, firm, StampStamper.Ink.FRESH, hard);
        check(each(hard, 3, 48)[1] > 1.2 * each(light, 3, 48)[1], "pressure prints darker");
    }

    static void rests() {
        // The stamper alone: a stroke starting soon after the last print carries its ink on; over a second later, afresh.
        StampStamper.State first = prints(stamp(1.2, .22, true, PartChoice.INK), 40, line(30, 630, 200, 1000), StampStamper.Ink.FRESH, new FloatTiles(W, H, 0));
        StampStamper.Ink left = first.left();
        check(left.load < .5 && left.last >= 1000, "a stroke leaves its ink and its last print's time: " + left.load + " at " + left.last);
        StampStamper.State soon = prints(stamp(1.2, .22, true, PartChoice.INK), 40, line(30, 90, 260, left.last + 400), left, new FloatTiles(W, H, 0));
        StampStamper.State rested = prints(stamp(1.2, .22, true, PartChoice.INK), 40, line(30, 90, 260, left.last + 1500), left, new FloatTiles(W, H, 0));
        check(soon.load <= left.load && rested.load > 2 * soon.load, "a short pause carries the ink on (" + soon.load + "), a rest inks it afresh (" + rested.load + ")");
        // Drawn on a page with a session: the holder's stamp carries its ink from stroke to stroke.
        WatercolorSession session = new WatercolorSession();
        ModularBrush b = stamp(1.2, .22, true, PartChoice.INK);
        double[] darkness = new double[3];
        long[] starts = {1000, 1400, 3400};
        for (int i = 0; i < 3; i++) {
            ToneDocument d = InkWashLayers.blank(W, H);
            DrawingStroke s = ModularStroke.start(d, b, 40, 1, session);
            CarrySmudgeChecks.feed(s, new double[][]{{300, 200, .7, starts[i]}});
            for (int y = 150; y < 250; y++) for (int x = 250; x < 350; x++) darkness[i] += 255 - d.compositeTone(x, y);
        }
        check(darkness[1] < .9 * darkness[0] && darkness[2] > .97 * darkness[0], "taps: the second soon after the first lighter (" + Math.round(darkness[1]) + " of " + Math.round(darkness[0])
                + "), one after a rest as dark again (" + Math.round(darkness[2]) + ")");
    }

    static void codes() {
        Map<String, String> kept = new HashMap<>();
        BrushLibrary.Store store = new BrushLibrary.Store() {
            @Override public String get(String key) { return kept.get(key); }
            @Override public void put(String key, String value) { if (value == null) kept.remove(key); else kept.put(key, value); }
        };
        StampNib.forget();
        StampNib.storeIn(store);
        try {
            StampNib nib = StampNib.cut(page(new int[]{50, 30, 69, 43}, new int[]{60, 44, 64, 60}), 255, new float[]{40, 90, 90, 40}, new float[]{20, 20, 70, 70}, 4, W, H);
            int id = StampNib.keep(nib);
            check(id == nib.id && kept.containsKey(StampNib.key(id)), "a cut nib is kept in the store under its id");
            ModularBrush b = stamp(.5, .1, false, PartChoice.INK).withNib(id);
            b.name = "potato";
            String code = ModularCode.encode(b);
            check(code.contains("_" + Long.toString(id & 0xffffffffL, 36) + ".") && code.matches("potato\\.M[0-9a-z]a.*"), "a Stamp's code is Tip choice 10 and carries its nib after the six: " + code);
            ModularBrush back = ModularCode.decode(code);
            check(back != null && back.nib == id && back.stamps() && ModularCode.encode(back).equals(code), "and reads back, nib and all, byte for byte");
            // A fresh start (another screen, the app opened again): the nib read from the store.
            StampNib.forget();
            ResolvedBrush r = ResolvedBrush.of(back, 60, 1);
            check(r.pattern.nib != StampNib.ROUND && r.pattern.nib.id == id && r.pattern.nib.width == nib.width && r.pattern.nib.height == nib.height, "the nib comes back from the store");
            check(back.key().equals(b.key()) && !b.key().equals(b.withNib(0).key()), "two stamps differ by their nibs");
            // Without a Stamp the nib isn't written: every other code is as before.
            ModularBrush ink = StippleChecks.mk(PartChoice.ROUND, PartChoice.STEADY);
            String plain = ModularCode.encode(ink);
            check(ModularCode.encode(ink.withNib(id)).equals(plain), "a brush without a Stamp writes no nib");
            for (ToolPresets.Preset p : ToolPresets.all()) check((ModularCode.encode(ModularCode.decode(p.code)).equals(p.code) || HandHatchChecks.handHatch(p.code)) && ModularCode.decode(p.code).nib == 0, p.name + "'s code byte for byte");
            // Kept for good beside the library's file, as saved brushes are: a fresh install reads it back from there.
            try {
                java.io.File folder = java.nio.file.Files.createTempDirectory("stamps").toFile(), file = new java.io.File(folder, BrushBackup.NAME);
                Map<String, String> first = new HashMap<>(), fresh = new HashMap<>();
                BrushLibrary.Store a = BrushBackup.backed(mapStore(first), file);
                a.put(StampNib.key(id), nib.write());
                java.io.File saved = BrushBackup.fileOf(file, StampNib.key(id));
                String again = BrushBackup.backed(mapStore(fresh), file).get(StampNib.key(id));
                check(saved.isFile() && saved.getParentFile().getName().equals(BrushBackup.NIBS) && nib.write().equals(again), "a nib is kept beside the library's file and read back from there");
                saved.delete(); saved.getParentFile().delete(); folder.delete();
            } catch (java.io.IOException e) { throw new AssertionError(e); }
            // Brush Lab's Cut Stamp: the lab's brush prints the nib, an Undo step.
            BrushLab lab = new BrushLab(new ToolSlots(mapStore(new HashMap<>()), new java.util.Random(3)), 0, new java.util.Random(4));
            lab.choose(BrushPart.TIP, PartChoice.STAMP);
            lab.cutNib(id);
            boolean cut = lab.parts().nib == id && lab.brush().parts.nib == id && lab.code().contains("_" + Long.toString(id & 0xffffffffL, 36) + ".")
                    && lab.status.equals(StampNib.DONE) && BrushLab.redraw(lab.parts().withNib(0), lab.parts()).part(BrushPart.TIP);
            lab.undo();
            check(cut && lab.parts().stamps() && lab.parts().nib == 0, "Brush Lab's Cut Stamp gives its Stamp the nib, and Undo takes it back");
            // A Stamp with no nib writes no seventh segment.
            String bare = ModularCode.encode(stamp(.5, .1, false, PartChoice.INK));
            check(bare.split("\\.")[1].split("_").length == 6, "a Stamp without a nib writes the six: " + bare);
        } finally { StampNib.storeIn(null); StampNib.forget(); }
        // Never rolled.
        java.util.Random random = new java.util.Random(11);
        for (ModularStyle s : ModularStyle.all()) for (int i = 0; i < 200; i++) check(s.roll(random).choice(BrushPart.TIP) != PartChoice.STAMP, s.name + " never rolls a Stamp");
        check(ModularFeatures.seedOnly(PartChoice.STAMP) && !ModularFeatures.varies(PartChoice.STAMP) && ModularFeatures.offered(PartChoice.STAMP), "offered in Brush Lab, never a variation's swap");
    }

    static void fallback() {
        StampNib.forget();
        // A nib the store doesn't hold (a code from another tablet), and a Stamp never cut: both print round.
        ModularBrush lost = ModularCode.decode(ModularCode.encode(stamp(.12, .22, true, PartChoice.INK).withNib(0x5eed)));
        check(lost != null && lost.nib == 0x5eed, "a code keeps its nib's id even without the nib");
        ResolvedBrush r = ResolvedBrush.of(lost, 60, 1), none = ResolvedBrush.of(stamp(.12, .22, true, PartChoice.INK), 60, 1);
        check(r.pattern.nib == StampNib.ROUND && none.pattern.nib == StampNib.ROUND, "without its nib a Stamp prints round");
        ToneDocument d = InkWashLayers.blank(W, H);
        DrawingStroke s = ModularStroke.start(d, lost, 60, 1, null);
        CarrySmudgeChecks.feed(s, line(100, 400, 200, 1000));
        check(ink(d) > 1000, "and draws");
        String code = ModularCode.encode(stamp(.12, .22, true, PartChoice.INK));
        int dot = code.lastIndexOf('.');
        ModularBrush odd = ModularCode.decode(code.substring(0, dot) + "_zzzzzzzzz" + code.substring(dot));
        check(odd != null && odd.nib == 0, "a seventh segment that doesn't read is no nib");
    }

    static void words() {
        ModularBrush b = stamp(.12, .22, true, PartChoice.INK);
        String plain = ModularWords.plainWords(b);
        check(plain.startsWith("Ink stamp. " + ModularWords.STAMPS + " " + ModularWords.FADES), "This brush: " + plain);
        String none = ModularWords.plainWords(stamp(.12, 0, true, PartChoice.INK));
        check(none.contains(ModularWords.STAMPS) && !none.contains(ModularWords.FADES), "Ink Fade None doesn't fade: " + none);
        check(ModularWords.caption(b).contains("stamp") && ModularWords.choiceWords(b, BrushPart.TIP).equals("Stamp"), "caption " + ModularWords.caption(b) + ", the Tip's module " + ModularWords.choiceWords(b, BrushPart.TIP));
        check(PartDial.INK_FADE.shown(0) .equals("None") && PartDial.INK_FADE.shown(.3).equals("Strong") && PartDial.INK_FADE.shown(.22).equals("22%") && PartDial.STAMP_SPACING.shown(.12).equals("12%"), "dial values");
        check(PartDial.HEAD_TURN.shown(0, b.part(BrushPart.TIP)).equals("On") && PartDial.HEAD_TURN.shown(1, b.part(BrushPart.TIP)).equals("Off"), "Turns On or Off");
        check(ModularSizes.size(b, InkWashSizes.Preset.MEDIUM) == ModularSizes.STAMP, "a Stamp's Medium is its own, whatever the style");
    }

    static void media() {
        // Ink, a wash and a printed (halftone) ink all lay the stamp; the halftone pure black and white.
        for (PartChoice m : new PartChoice[]{PartChoice.INK, PartChoice.WASH, PartChoice.COVER, PartChoice.CHARCOAL}) {
            ToneDocument d = InkWashLayers.blank(W, H);
            DrawingStroke s = ModularStroke.start(d, stamp(.5, .1, true, m), 60, 1, null);
            CarrySmudgeChecks.feed(s, line(100, 400, 200, 1000));
            check(ink(d) > 1000, m + " lays the stamp");
        }
        ModularBrush printed = stamp(.5, 0, true, PartChoice.INK);
        printed.part(BrushPart.MEDIUM).set(PartDial.SCREEN, 1);
        ToneDocument d = InkWashLayers.blank(W, H);
        DrawingStroke s = ModularStroke.start(d, printed, 60, 1, null);
        CarrySmudgeChecks.feed(s, line(100, 400, 200, 1000));
        int grey = 0, dark = 0;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) { int t = d.compositeTone(x, y); if (t < 250) dark++; if (t > 5 && t < 250) grey++; }
        check(dark > 500 && grey == 0, "Print Shading prints it in black and white: " + dark + " dark, " + grey + " grey");
    }

    static void quick() {
        for (PartChoice m : new PartChoice[]{PartChoice.INK, PartChoice.WASH}) {
            ModularBrush b = stamp(.3, .22, true, m);
            ResolvedBrush r = ResolvedBrush.of(b, ModularSizes.STAMP, 1);
            check(QuickStroke.quick(r), m + ": a Stamp at its Medium size is drawn quick");
            double[][] path = FlickFlingChecks.line(100, 700, 300, .8);
            ToneDocument live = FlickFlingChecks.draw(b, ModularSizes.STAMP, path, false), fast = FlickFlingChecks.draw(b, ModularSizes.STAMP, path, true);
            int differ = 0;
            for (int y = 0; y < FlickFlingChecks.H; y++) for (int x = 0; x < FlickFlingChecks.W; x++) if (live.compositeTone(x, y) != fast.compositeTone(x, y)) differ++;
            check(differ == 0 && FlickFlingChecks.ink(live, 0, FlickFlingChecks.W, 0, FlickFlingChecks.H) > 5000, m + ": quick ends as live (" + differ + " px differ)");
        }
    }

    static BrushLibrary.Store mapStore(Map<String, String> kept) {
        return new BrushLibrary.Store() {
            @Override public String get(String key) { return kept.get(key); }
            @Override public void put(String key, String value) { if (value == null) kept.remove(key); else kept.put(key, value); }
        };
    }

    /** How many of the page's pixels are darker than the paper. */
    static int ink(ToneDocument d) {
        int n = 0;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (d.compositeTone(x, y) < 250) n++;
        return n;
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
