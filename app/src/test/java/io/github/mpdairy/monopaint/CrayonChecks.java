package io.github.mpdairy.monopaint;

import java.util.Locale;

/**
 * Crayon (mischief-lab-2.html's fourth pad; {@link Crayon}, a Medium choice): codes, Medium choice 7 with Wax and
 * Scratch, every older code byte for byte; light pressure catches only the tooth's peaks, hard pressure goes solid in
 * the middle and stays broken at the edge; the same stroke draws the same pixels; a second stroke builds up over the
 * first (wax takes wax); a wash slides off it (its wax on the masking fluid); Scratch scrapes a black patch back to
 * white; one Undo takes a crayon stroke and its wax; drawn quick it ends as live; its lift redraws nothing it showed;
 * never rolled; words; and fast enough.
 */
public final class CrayonChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        verbose = args.length > 0;
        codes(); pressure(); same(); buildUp(); resists(); scratch(); undo(); quick(); lift(); words(); speed();
        System.out.println("PASS: Crayon is Medium choice 7 (Wax, Scratch; every older code byte for byte), never rolled; light pressure only the tooth's peaks, hard solid "
                + "in the middle and broken at the edge; the same stroke the same pixels; layers build up; a wash slides off it; Scratch scrapes black back to white; "
                + "one Undo takes it with its wax; drawn quick it ends as live; its lift redraws nothing it showed; its words; within the budget a sample");
    }
    static boolean verbose;
    static void note(String s) { if (verbose) System.out.println(s); }
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    static final int W = BlendChecks.W, H = BlendChecks.H;

    /** A Crayon: round tip, Instant, steady, smooth paper, its Wax and Scratch. */
    static ModularBrush crayon(double wax, boolean scratch) {
        ModularBrush b = StippleChecks.mk(PartChoice.ROUND, PartChoice.STEADY);
        ModularBrush.Part m = new ModularBrush.Part(PartChoice.CRAYON);
        m.set(PartDial.WAX, wax);
        m.set(PartDial.SCRATCH, scratch ? 1 : 0);
        b.set(BrushPart.MEDIUM, m);
        b.set(BrushPart.LIFE, new ModularBrush.Part(PartChoice.NONE));
        return b.normalize();
    }
    /** A straight stroke at {@code y} from {@code x0} to {@code x1} at pen pressure {@code p}, a sample every 2.5 px. */
    static double[][] across(double x0, double x1, double y, double p) {
        double[][] o = BlendChecks.across(x0, x1, y);
        for (double[] s : o) s[2] = p;
        return o;
    }
    static void draw(ToneDocument d, ModularBrush b, double size, double[][] path) { BlendChecks.live(d, b, size, 1, null, path); }
    static int[] layer(ToneDocument d, int layer) { return BlendChecks.layerTones(d, layer); }
    /** How many pixels in rows {@code y0..y1}, columns 100..380, are darker than {@code under}. */
    static int dark(int[] tones, int y0, int y1, int under) {
        int n = 0;
        for (int y = y0; y <= y1; y++) for (int x = 100; x <= 380; x++) if (tones[y * W + x] < under) n++;
        return n;
    }

    static void codes() {
        check(PartChoice.CRAYON.index() == 7 && PartChoice.of(BrushPart.MEDIUM, 7) == PartChoice.CRAYON && PartChoice.of(BrushPart.MEDIUM, 8) == null
                && PartChoice.CRAYON.key.equals("crayon") && PartChoice.CRAYON.label.equals("Crayon"), "Crayon is Medium choice 7");
        check(PartModelChecks.dials(PartChoice.CRAYON).equals("wax scratch") && PartChoice.CRAYON.defaultOf(PartDial.WAX) == .6 && PartChoice.CRAYON.defaultOf(PartDial.SCRATCH) == 0
                && String.join(",", PartChoice.CRAYON.optionsOf(PartDial.SCRATCH)).equals("Off,On") && PartChoice.CRAYON.macro == PartDial.WAX
                && PartChoice.CRAYON.words[0].equals("chalky") && PartChoice.CRAYON.words[1].equals("waxy") && !PartChoice.CRAYON.has(PartDial.ERASABLE), "its dials: Wax (.6), Scratch (Off, On)");
        ModularBrush b = crayon(.37, true);
        b.name = "wax";
        String code = ModularCode.encode(b);
        ModularBrush back = ModularCode.decode(code);
        check(code.contains("_7") && back != null && back.choice(BrushPart.MEDIUM) == PartChoice.CRAYON && ModularCode.encode(back).equals(code)
                && Math.abs(back.part(BrushPart.MEDIUM).get(PartDial.WAX) - .37) < 1e-3 && back.part(BrushPart.MEDIUM).get(PartDial.SCRATCH) == 1, "a crayon's code round-trips: " + code);
        // Every other code byte for byte: Basic tools and the checks' own.
        for (ToolPresets.Preset p : ToolPresets.all()) {
            ModularBrush o = ModularCode.decode(p.code);
            if (o != null) check(ModularCode.encode(o).equals(p.code) || HandHatchChecks.handHatch(p.code), "an older code writes back byte for byte (Hand Hatch's as Hatch): " + p.code);
        }
        for (String c : new String[]{ResistChecks.FLUID, ResistChecks.WAX, ResistChecks.INK, BlendChecks.CHALK})
            check(ModularCode.encode(ModularCode.decode(c)).equals(c), "an older code writes back byte for byte: " + c);
        check(ModularFeatures.seedOnly(PartChoice.CRAYON) && !ModularFeatures.varies(PartChoice.CRAYON), "never a variation's swap");
        for (int i = 0; i < 1400; i++) {
            ModularBrush r = ModularStyle.all().get(i % ModularStyle.all().size()).roll(new java.util.Random(i));
            check(r.choice(BrushPart.MEDIUM) != PartChoice.CRAYON, "never rolled");
        }
        // Resolved: a stick on the ink layer without a smudge, on paper, its Scratch a blade laid white.
        ModularBrush smeared = crayon(.6, false);
        smeared.set(BrushPart.CARRY, new ModularBrush.Part(PartChoice.SMUDGE));
        smeared.set(BrushPart.LIFE, new ModularBrush.Part(PartChoice.FLOAT));
        ResolvedBrush r = ResolvedBrush.of(smeared, 30, 1), s = ResolvedBrush.of(crayon(.6, true), 30, 1);
        check(r.rule == ResolvedBrush.Rule.CRAYON && r.carry == null && r.flt == null && r.life == ResolvedBrush.Life.WET && r.waxes() && r.stick() && r.pen.crayon && ModularSizes.inks(smeared),
                "resolved: a crayon line, no smudge, on paper, laying wax");
        check(s.scratch && !s.waxes() && !s.pen.crayon && s.blendMode == ResolvedBrush.BlendMode.WHITE && Math.abs(s.inkSize - r.inkSize * .3) < 1e-9, "its Scratch: a blade a third as wide, laid white, no wax");
        check(ModularSizes.size(smeared, InkWashSizes.Preset.MEDIUM) == ModularSizes.CRAYON, "a crayon's Medium size is its own in every style");
    }

    static void pressure() {
        ToneDocument light = InkWashLayers.blank(W, H), hard = InkWashLayers.blank(W, H);
        draw(light, crayon(.6, false), 40, across(60, 420, 180, .25));
        draw(hard, crayon(.6, false), 40, across(60, 420, 180, 1));
        int[] l = layer(light, InkWashLayers.INK), h = layer(hard, InkWashLayers.INK);
        int lightInk = dark(l, 160, 200, 200), hardInk = dark(h, 160, 200, 200);
        // The middle rows of the hard stroke, and its edge band (the outer rows its outline wavers through).
        int middle = dark(h, 176, 184, 80), middleAll = 9 * 281;
        int r = (int) Math.round(ResolvedBrush.of(crayon(.6, false), 40, 1).inkSize / 2);
        int edge = 0, edgePaper = 0;
        // The outline wavers from .8 to 1.3 of the rim.
        for (int y = 180 - (int) (1.25 * r); y <= 180 + (int) (1.25 * r); y++) if (Math.abs(y - 180) >= .85 * r) for (int x = 100; x <= 380; x++) { edge++; if (h[y * W + x] > 230) edgePaper++; }
        note(String.format(Locale.ROOT, "light %d px, hard %d px; hard middle %d of %d solid; edge band %d of %d paper", lightInk, hardInk, middle, middleAll, edgePaper, edge));
        check(lightInk > 100 && lightInk * 3 < hardInk, "light pressure catches only the tooth's peaks: " + lightInk + " px against hard's " + hardInk);
        check(middle > .9 * middleAll, "pressed hard the middle goes solid: " + middle + " of " + middleAll);
        check(edgePaper > .15 * edge && edgePaper < .9 * edge, "and the edge stays broken: " + edgePaper + " of " + edge + " px paper");
        // Flecks: a few specks just past the outline.
        int past = 0;
        for (int y = 180 - (int) (1.4 * r); y < 180 - (int) (1.1 * r); y++) for (int x = 100; x <= 380; x++) if (h[y * W + x] < 230) past++;
        check(past > 0, "a few flecks just past the edge: " + past);
    }

    static void same() {
        ToneDocument a = InkWashLayers.blank(W, H), b = InkWashLayers.blank(W, H);
        draw(a, crayon(.6, false), 40, BlendChecks.wave());
        draw(b, crayon(.6, false), 40, BlendChecks.wave());
        check(ResistChecks.changed(ResistChecks.state(a), ResistChecks.state(b), null) == 0, "the same stroke on a fresh page draws the same pixels");
        // Another crayon brush (another seed) catches the same grain: the tooth is the page's.
        ModularBrush other = crayon(.6, false).copy(99);
        ToneDocument c = InkWashLayers.blank(W, H);
        draw(c, other, 40, BlendChecks.wave());
        check(java.util.Arrays.equals(layer(a, InkWashLayers.INK), layer(c, InkWashLayers.INK)), "every crayon catches the same grain");
    }

    static void buildUp() {
        ToneDocument once = InkWashLayers.blank(W, H), twice = InkWashLayers.blank(W, H);
        double[][] path = across(60, 420, 180, .45);
        draw(once, crayon(.6, false), 40, path);
        draw(twice, crayon(.6, false), 40, path);
        draw(twice, crayon(.6, false), 40, path);
        long a = BlendChecks.darkness(once, InkWashLayers.INK), b = BlendChecks.darkness(twice, InkWashLayers.INK);
        // Wax off builds up less: what is there lifts the surface by Wax.
        ToneDocument chalky = InkWashLayers.blank(W, H);
        draw(chalky, crayon(0, false), 40, path);
        long c0 = BlendChecks.darkness(chalky, InkWashLayers.INK);
        draw(chalky, crayon(0, false), 40, path);
        long c1 = BlendChecks.darkness(chalky, InkWashLayers.INK);
        note("build-up: once " + a + ", twice " + b + "; Wax off " + c0 + ", twice " + c1);
        check(b > 1.15 * a, "a second stroke builds up over the first (wax takes wax): " + b + " against " + a);
        check(c1 - c0 < b - a, "and with Wax off, less: " + (c1 - c0) + " against " + (b - a));
    }

    static void resists() {
        ModularBrush wash = GravityChecks.preset("Soft wash");
        for (double wax : new double[]{.6, 0}) {
            ToneDocument d = InkWashLayers.blank(W, H);
            draw(d, crayon(wax, false), 40, across(60, 420, 180, 1));
            int[] ink = layer(d, InkWashLayers.INK);
            ResistChecks.dried(d, wash, 160, CarrySmudgeChecks.wave(), null);
            int[] w = layer(d, InkWashLayers.WASH);
            double on = 0, beside = 0; int n = 0, m = 0;
            for (int y = 150; y <= 210; y++) for (int x = 120; x <= 360; x++) {
                if (ink[y * W + x] < 60) { on += 255 - w[y * W + x]; n++; }
                else if (ink[y * W + x] == 255 && Math.abs(y - 180) > 25) { beside += 255 - w[y * W + x]; m++; }
            }
            on /= Math.max(1, n); beside /= Math.max(1, m);
            note(String.format(Locale.ROOT, "Wax %.1f: wash on the crayon %.1f, beside %.1f", wax, on, beside));
            if (wax > 0) check(n > 1000 && beside > 20 && on < .15 * beside, "a wash slides off the wax: " + on + " on it against " + beside + " beside");
            else check(on > .6 * beside, "with Wax off a wash takes on it: " + on + " against " + beside);
            // The wax isn't hatched as fluid that rubs off.
            int hatched = 0;
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (d.maskHatch(x, y) >= 0) hatched++;
            check(hatched == 0, "its wax is never hatched: " + hatched);
        }
    }

    static void scratch() {
        // A black page: hard crayon rows, solid wax; then the blade through it.
        ToneDocument d = InkWashLayers.blank(W, H);
        for (int y = 140; y <= 220; y += 6) draw(d, crayon(.6, false), 40, across(60, 420, y, 1));
        int[] before = BlendChecks.shown(d);
        draw(d, crayon(.6, true), 40, across(80, 400, 180, 1));
        int[] after = BlendChecks.shown(d);
        int black = 0, white = 0;
        for (int x = 120; x <= 360; x++) { if (before[180 * W + x] < 60) black++; if (after[180 * W + x] > 200) white++; }
        note("scratch: " + black + " black before, " + white + " white after, of 241");
        check(black > 220 && white > 180, "Scratch scrapes a black patch back to white: " + white + " of 241 px");
        int beside = 0;
        for (int x = 120; x <= 360; x++) if (Math.abs(after[150 * W + x] - before[150 * W + x]) > 0) beside++;
        check(beside == 0, "a thin blade: the patch beside it as it was (" + beside + " px changed)");
    }

    static void undo() {
        ToneDocument d = InkWashLayers.blank(W, H);
        byte[][] before = ResistChecks.state(d);
        draw(d, crayon(.6, false), 40, across(60, 420, 180, 1));
        byte[][] laid = ResistChecks.state(d);
        check(ResistChecks.changed(before, laid, null) > 1000, "a crayon stroke lays wax");
        check(d.undo() && ResistChecks.changed(before, ResistChecks.state(d), null) == 0, "one Undo takes it back, its wax too");
        check(d.redo() && ResistChecks.changed(laid, ResistChecks.state(d), null) == 0, "and Redo lays it again");
    }

    static void quick() {
        for (boolean scratch : new boolean[]{false, true}) for (double size : new double[]{40, 160}) {
            ToneDocument live = InkWashLayers.blank(W, H), quick = InkWashLayers.blank(W, H);
            if (scratch) for (ToneDocument d : new ToneDocument[]{live, quick}) BlendChecks.lay(d, InkWashLayers.INK, (x, y) -> 20, 1);
            BlendChecks.live(live, crayon(.6, scratch), size, 1, null, BlendChecks.wave());
            BlendChecks.quick(quick, crayon(.6, scratch), size, 1, null, BlendChecks.wave());
            int differ = ResistChecks.changed(ResistChecks.state(live), ResistChecks.state(quick), null);
            check(differ == 0, (scratch ? "Scratch" : "Crayon") + " at " + size + " px drawn quick ends as drawn live: " + differ + " pixels differ");
        }
    }

    /**
     * The lift (the owner, 2026-10-29: "this crayon i've been using has a long delay between drawing and the line showing
     * up on screen"): a live crayon's wax goes onto the masking fluid as it is drawn, so its lift only writes it, and what
     * the pen showed is final: the lift leaves nothing to redraw but where the fluid's hatching comes or goes (it redrew
     * the whole stroke's box). Over earlier wax and over masking fluid, the page ends byte for byte as drawn quick, whose
     * render still composes the wax at the lift.
     */
    static void lift() {
        for (boolean fluid : new boolean[]{false, true}) {
            ToneDocument live = fluid ? ResistChecks.resisted(ResistChecks.FLUID) : InkWashLayers.blank(W, H), quick = fluid ? ResistChecks.resisted(ResistChecks.FLUID) : InkWashLayers.blank(W, H);
            for (ToneDocument d : new ToneDocument[]{live, quick}) draw(d, crayon(.6, false), 40, across(60, 420, 200, .9));
            DrawingStroke s = ModularStroke.start(live, crayon(.6, false), 40, 1, null);
            double[][] path = BlendChecks.wave();
            for (int i = 0; i < path.length; i++) {
                s.sample((float) path[i][0], (float) path[i][1], ToolSettings.pressure((float) path[i][2]), 0, 0, (long) path[i][3]);
                if (i % 3 == 2) s.present();
            }
            s.present();
            int[] shown = new int[W * H], after = new int[W * H];
            live.render(shown, 0, 0, W, H);
            live.clearDirty();
            check(s.finish(), "the stroke laid something");
            int[] dirty = live.dirty();
            live.render(after, 0, 0, W, H);
            int changed = 0, outside = 0;
            for (int i = 0; i < shown.length; i++) if (shown[i] != after[i]) {
                changed++;
                int x = i % W, y = i / W;
                if (dirty == null || x < dirty[0] || y < dirty[1] || x >= dirty[2] || y >= dirty[3]) outside++;
            }
            note((fluid ? "over fluid" : "on paper") + ": the lift left " + (dirty == null ? "nothing" : java.util.Arrays.toString(dirty)) + " to redraw, " + changed + " px changed");
            check(outside == 0, "the lift redraws every pixel it changes: " + outside + " outside");
            if (!fluid) check(dirty == null && changed == 0, "on paper the lift changes nothing shown and redraws nothing: " + java.util.Arrays.toString(dirty));
            BlendChecks.quick(quick, crayon(.6, false), 40, 1, null, path);
            int differ = ResistChecks.changed(ResistChecks.state(live), ResistChecks.state(quick), null);
            check(differ == 0, "its wax laid as drawn ends as composed at the lift" + (fluid ? " over masking fluid: " : ": ") + differ + " pixels differ");
        }
    }

    static void words() {
        ModularBrush c = crayon(.6, false), chalky = crayon(0, false), s = crayon(.6, true);
        String said = ModularWords.plainWords(c);
        check(said.startsWith("Wax crayon. Builds up, resists washes."), "This brush: " + said);
        check(ModularWords.plainWords(chalky).startsWith("Wax crayon. Builds up.") && ModularWords.plainWords(s).startsWith("Crayon scratch. Scrapes back to white."),
                "Wax off and Scratch: " + ModularWords.plainWords(chalky) + " / " + ModularWords.plainWords(s));
        check(ModularWords.choiceWords(c, BrushPart.MEDIUM).equals("Crayon") && ModularWords.choiceWords(s, BrushPart.MEDIUM).equals("Scratch"), "the Medium card: Crayon, Scratch");
        check(ModularWords.caption(c).contains("crayon") && ModularWords.caption(s).contains("scratch") && ModularWords.erasable(c).isEmpty(), "captions tag crayon or scratch: " + ModularWords.caption(c));
        ModularBrush stippled = c.copy();
        stippled.set(BrushPart.TIP, new ModularBrush.Part(PartChoice.STAMP));
        stippled.set(BrushPart.CARRY, new ModularBrush.Part(PartChoice.SMUDGE));
        String st = ModularWords.plainWords(stippled.normalize());
        check(st.startsWith("Wax crayon. ") && !st.contains("Smears"), "named for itself whatever its tip, and smears nothing: " + st);
        check(ModularWords.caption(stippled).stream().noneMatch(t -> t.equals("smear")), "no smudge tag");
        check(PartDial.WAX.shown(.6).equals("0.60") && PartDial.SCRATCH.shown(1).equals("On"), "its dials shown: " + PartDial.WAX.shown(.6));
    }

    /** A long crayon stroke within the budget a sample (0.1 ms on the desktop; ModularBenchChecks times the rest). */
    static void speed() {
        double[][] path = new double[801][];
        for (int i = 0; i <= 800; i++) { double t = i / 800.0; path[i] = new double[]{40 + 400 * t, 180 + 80 * Math.sin(6 * Math.PI * t), .3 + .6 * t, i * 4}; }
        long best = Long.MAX_VALUE;
        for (int k = 0; k < 6; k++) {
            ToneDocument d = InkWashLayers.blank(W, H);
            long t0 = System.nanoTime();
            draw(d, crayon(.6, false), 30, path);
            best = Math.min(best, System.nanoTime() - t0);
        }
        double per = best / 1e6 / path.length;
        note(String.format(Locale.ROOT, "%.4f ms a sample", per));
        Timing.limit(per < .1, String.format(Locale.ROOT, "a crayon stroke within 0.1 ms a sample: %.4f ms", per));
    }
}
