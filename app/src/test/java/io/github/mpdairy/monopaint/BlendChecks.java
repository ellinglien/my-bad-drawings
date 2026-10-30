package io.github.mpdairy.monopaint;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.Locale;
import java.util.Random;
import java.util.function.IntBinaryOperator;

/**
 * Blend (the new modules spec, a Medium dial): the medium's own way, White, Behind, Invert. Codes; golden pages against
 * the web lab (scripts/blend_goldens.cjs); Behind leaving every mark byte for byte; White over dark; Invert twice; the
 * eraser's rule for each; Smudge, Slow, gravity and Float; quick strokes ending as live; several cores; rolls from the
 * seed; words and Brush Lab. {@code java BlendChecks pairs <folder>} writes each golden beside the app's drawing.
 */
public final class BlendChecks {
    public static void main(String[] args) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        if (args.length > 1 && args[0].equals("pairs")) { pairs(new File(args[1])); return; }
        verbose = args.length > 0;
        codes(); goldens(); behind(); white(); invert(); erasable(); smudge(); drying(); quick(); cores(); rolls(); words(); lab();
        System.out.println("PASS: Blend is a Medium dial after Erasable, written only when not the medium's own way (old codes byte for byte), the lab's codes round-trip; White, Behind and Invert with ink and opaque paint draw the web lab's golden pages (at 16 greys: ink at most 0.1% of pixels one grey off, paint 0.5% along its soft edge), and with a wash and charcoal are the lab's rule on the app's own mark to two grey steps; Behind leaves every mark already there byte for byte, on its own layer and the ink above; White lays white over dark and nothing on paper, and covers the wash under the ink layer; Invert turns paper black and black white, and twice gives the page back; the eraser leaves what each keeps (White over permanent ink stays, over pencil goes; Behind's wash goes by its share under ink that stays; the eraser never darkens); a smudge then its Blend; White and Invert never settle, run or drip, Behind settles only where it landed; on water it does nothing; quick strokes end as drawn live; several cores draw it as one; rolls give it rarely from the seed, every other roll as before; the words and Brush Lab say it");
    }
    static boolean verbose;
    static void note(String s) { if (verbose) System.out.println(s); }
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    static final int W = 480, H = 360;
    static final String WHITE_GEL = "white-gel-pen.M00i000000000at_000zz0000000000000001_0_000_1jz_0000000000000000000nz0.1y9",
            WASH_BEHIND = "wash-behind.M60i000000000at_1ulat00770000ee2_0_000_1jz_0000000000000000000nz0.11kx",
            INVERT_INK = "invert-ink.M00i000000000at_000zz0000000000000003_0_000_1jz_0000000000000000000nz0.1y9";
    /** The golden brushes (scripts/blend_goldens.cjs lists the same): name, code, size px, darkness, marks, the marks' layer. */
    static final Object[][] GOLDENS = {
            {"white-gel", WHITE_GEL, 8.0, 1.0, "ground", InkWashLayers.WASH},
            {"white-gel-light", WHITE_GEL, 14.0, .5, "ground", InkWashLayers.WASH},
            {"invert-ink", INVERT_INK, 30.0, 1.0, "lines", InkWashLayers.WASH},
            {"paint-behind", "paint-behind.M80i000000000at_2zz00at2_0_000_1jz_0000000000000000000nz0.13ztx3e", 60.0, .6, "lines", InkWashLayers.WASH},
            {"white-paint", "white-paint.M80i000000000at_2zz00at1_0_000_1jz_0000000000000000000nz0.13ztx3e", 40.0, .7, "lines", InkWashLayers.WASH},
    };

    static final String CHALK = "chalk.M50i000000000at_3atee00we1_0_13x_1jz_0000000000000000000nz0.13ax4gw";
    /** Golden {@code name}'s code. */
    static String code(String name) { for (Object[] g : GOLDENS) if (g[0].equals(name)) return (String)g[1]; throw new IllegalArgumentException(name); }

    // ---- pages, marks and strokes ----

    /** The marks' tone at {@code x, y} (blend_goldens.cjs mark): lines, a grey band and hatching; or a dark ground with a lighter band. */
    static int mark(String kind, int x, int y) {
        if (kind.equals("ground")) return x >= 60 && x < 420 ? (x >= 200 && x < 260 ? 150 : 40) : 255;
        return CarrySmudgeChecks.mark(x, y);
    }
    /** A blank one-pair page with {@code tone(x, y)} (255: nothing) laid on {@code layer}, at Erasable {@code erasable}. */
    static ToneDocument page(int layer, IntBinaryOperator tone) { return lay(InkWashLayers.blank(W, H), layer, tone, 1); }
    static ToneDocument lay(ToneDocument d, int layer, IntBinaryOperator tone, double erasable) { return lay(d, layer, tone, erasable, 0); }
    /** As above, of {@code kinds}: on that kind's plane ({@link ToneDocument#planeOf}). */
    static ToneDocument lay(ToneDocument d, int layer, IntBinaryOperator tone, double erasable, int kinds) {
        d.selectLayer(layer);
        d.begin(erasable, kinds);
        int[] row = new int[d.width];
        for (int y = 0; y < d.height; y++) {
            for (int x = 0; x < d.width; x++) { int t = tone.applyAsInt(x, y); row[x] = t >= 255 ? -1 : t; }
            d.setTones(0, y, d.width, 1, row, null);
        }
        d.finish();
        return d;
    }
    static ModularBrush brush(String code) { ModularBrush b = ModularCode.decode(code); check(b != null, "decodes: " + code); return b; }
    /** {@code b} with Blend {@code option}. */
    static ModularBrush blend(ModularBrush b, int option) { ModularBrush c = b.copy(); c.part(BrushPart.MEDIUM).set(PartDial.M_BLEND, option); return c; }
    static ModularBrush life(ModularBrush b, PartChoice life) { ModularBrush c = b.copy(); c.set(BrushPart.LIFE, new ModularBrush.Part(life)); return c; }
    static ModularBrush erasable(ModularBrush b, double e) { ModularBrush c = b.copy(); c.part(BrushPart.MEDIUM).set(PartDial.ERASABLE, e); return c; }
    static double[][] wave() { return CarrySmudgeChecks.wave(); }
    /** A straight stroke at {@code y} from {@code x0} to {@code x1}, pressure travel .8. */
    static double[][] across(double x0, double x1, double y) { return CarrySmudgeChecks.across(x0, x1, y); }
    static void live(ToneDocument d, ModularBrush b, double size, double darkness, WatercolorSession session, double[][] path) {
        CarrySmudgeChecks.live(d, b, size, darkness, session, path);
    }
    static void quick(ToneDocument d, ModularBrush b, double size, double darkness, WatercolorSession session, double[][] path) {
        CarrySmudgeChecks.feed(QuickStroke.quickly(d, b, size, darkness, session), path);
    }
    static int q(int tone) { return Math.round(tone / 17f); }
    static int[] shown(ToneDocument d) { int[] t = new int[W * H]; for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) t[y * W + x] = d.compositeTone(x, y); return t; }
    /** Every layer's tones, coverages and shares. */
    static byte[][] all(ToneDocument d) {
        int n = d.layerCount();
        byte[][] out = new byte[3 * n][];
        for (int i = 0; i < n; i++) { byte[][] l = ErasableChecks.layer(d, i); out[3 * i] = l[0]; out[3 * i + 1] = l[1]; out[3 * i + 2] = l[2]; }
        return out;
    }
    static boolean same(byte[][] a, byte[][] b) { return ErasableChecks.same(a, b); }

    // ---- 1. codes ----

    static void codes() {
        // The lab's codes (newmods.js presets and goldens) read and write back byte for byte, with their Blend.
        String[] lab = {WHITE_GEL, WASH_BEHIND, INVERT_INK, code("paint-behind"), code("white-paint"), CHALK};
        int[] options = {1, 2, 3, 2, 1, 1};
        for (int i = 0; i < lab.length; i++) {
            ModularBrush b = brush(lab[i]);
            check(ModularCode.encode(b).equals(lab[i]) && (int) b.part(BrushPart.MEDIUM).get(PartDial.M_BLEND) == options[i], "The lab's code reads with its Blend and writes back: " + lab[i]);
        }
        check(WASH_BEHIND.contains("_1ulat00770000ee2_") && (code("paint-behind")).contains("_2zz00at2_"), "Blend is the medium's last digit, after Erasable (the spec's Opaque behind _2zz00at2_)");
        // Blend 0 is not written: every medium at its own way keeps its code, and a Blend adds one digit (Erasable's two before it when they were left out).
        for (PartChoice m : BrushPart.MEDIUM.choices()) {
            // Resist, Screentone and Crayon have no Blend of their own (ResistChecks, ScreenChecks, CrayonChecks).
            if (m == PartChoice.RESIST || m == PartChoice.TONE || m == PartChoice.CRAYON) continue;
            ModularBrush b = brush(WHITE_GEL);
            b.set(BrushPart.MEDIUM, ModularBrush.switchTo(b.part(BrushPart.MEDIUM), m));
            b.part(BrushPart.MEDIUM).set(PartDial.ERASABLE, 1);
            String own = ModularCode.encode(blend(b, 0).normalize());
            for (int o = 1; o < 4; o++) {
                String code = ModularCode.encode(blend(b, o).normalize());
                ModularBrush back = ModularCode.decode(code);
                check(code.length() > own.length() && code.split("_")[1].endsWith("zz" + o) && (int) back.part(BrushPart.MEDIUM).get(PartDial.M_BLEND) == o && ModularCode.encode(back).equals(code), m + " Blend " + o + " round-trips: " + code);
            }
        }
        // A medium chosen starts at its own way, as the lab's switchChoice (Blend isn't among what a swap keeps).
        ModularBrush w = brush(WHITE_GEL);
        check((int) ModularBrush.switchTo(w.part(BrushPart.MEDIUM), PartChoice.WASH).get(PartDial.M_BLEND) == 0 && new ModularBrush.Part(PartChoice.WASH).get(PartDial.M_BLEND) == 0, "A medium chosen is its own way");
        // ResolvedBrush: White and Invert carry no pigment (no settle, gravity or dust); on water Blend does nothing.
        ModularBrush slow = life(brush(WASH_BEHIND), PartChoice.DRYING);
        slow.part(BrushPart.LIFE).set(PartDial.SETTLE, .6); slow.part(BrushPart.LIFE).set(PartDial.RUNS, .5);
        check(ResolvedBrush.of(slow, 60, 1).settle > 0 && ResolvedBrush.of(slow, 60, 1).grav != null && ResolvedBrush.of(slow, 60, 1).blendMode == ResolvedBrush.BlendMode.BEHIND, "Behind settles and runs");
        for (int o : new int[]{1, 3}) {
            ResolvedBrush r = ResolvedBrush.of(blend(slow, o), 60, 1);
            check(r.settle == 0 && r.grav == null && !r.settlesAfterLift && r.heldBack == 0, "White and Invert don't settle, run or drip: " + o);
        }
        ModularBrush chalk = brush(CHALK);
        chalk.part(BrushPart.MEDIUM).set(PartDial.DUST, .8);
        check(ResolvedBrush.of(chalk, 30, 1).dust == 0 && ResolvedBrush.of(blend(chalk, 0), 30, 1).dust > 0, "Chalk sheds no darkening dust");
        check(ResolvedBrush.of(life(brush(WHITE_GEL), PartChoice.FLOAT), 8, 1).blendMode == ResolvedBrush.BlendMode.OWN, "On water Blend does nothing");
    }

    // ---- 2. golden pages against the web lab ----

    /** The app's drawing of golden {@code g}: the marks on their layer, its brush along the wave on its own. */
    static ToneDocument golden(Object[] g) {
        ModularBrush b = brush((String)g[1]);
        String kind = (String)g[4];
        ToneDocument d = page((Integer)g[5], (x, y) -> mark(kind, x, y));
        // On every pixel, as the web lab works it out: the lattice's own softness is LatticeChecks'.
        LatticeChecks.everyPixel(() -> live(d, b, (Double)g[2], (Double)g[3], null, wave()));
        return d;
    }
    static BufferedImage lab(String name) throws Exception { return javax.imageio.ImageIO.read(new File("app/src/test/resources/blend/" + name + ".png")); }
    static void goldens() throws Exception {
        for (Object[] g : GOLDENS) {
            ToneDocument d = golden(g);
            BufferedImage lab = lab((String)g[0]);
            String kind = (String)g[4];
            int off = 0, far = 0, changed = 0;
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
                int a = q(d.compositeTone(x, y)), b = q(lab.getRaster().getSample(x, y, 0)), m = q(mark(kind, x, y));
                if (b != m) changed++;
                if (Math.abs(a - b) == 1) off++; else if (a != b) far++;
            }
            note(String.format(Locale.ROOT, "golden %-16s changed %6d, one grey off %5d (%.3f%%), further %d", g[0], changed, off, 100.0 * off / (W * H), far));
            check(changed > 500, g[0] + ": the lab's stroke changes the page: " + changed);
            // Opaque paint's own soft edge lies a pixel off the lab's along the stroke (the app's paint body, tuned for
            // speed): there, and where that edge crosses a line, it differs; ink matches all but a few pixels.
            boolean paint = brush((String)g[1]).choice(BrushPart.MEDIUM) == PartChoice.COVER;
            int offMost = paint ? W * H / 200 : W * H / 1000, farMost = paint ? W * H / 4000 : 0;
            if (System.getProperty("allGoldens") == null) check(far <= farMost && off <= offMost, g[0] + " draws the lab's page at 16 greys: " + off + " pixels one grey off, " + far + " further");
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
                img.getRaster().setSample(x, H + 8 + y, 0, a == b ? 255 : Math.abs(a - b) == 1 ? 160 : 0);
            }
            javax.imageio.ImageIO.write(img, "png", new File(out, "golden-" + g[0] + ".png"));
        }
        System.out.println("wrote " + out);
    }

    // ---- 3. Behind: only where the page is blank ----

    /** The marks page for Behind: {@link #mark} lines on the ink layer, a darker band and a light one on the wash layer. */
    static ToneDocument behindPage() {
        // Pen lines on the pen plane, over the washes (kind layers: old paint of no kind would lie under every plane).
        ToneDocument d = lay(InkWashLayers.blank(W, H), InkWashLayers.INK, (x, y) -> mark("lines", x, y), 1, ToneDocument.KIND_PEN);
        // The washes on the wash plane, where a wash's Behind goes behind them (within its own plane).
        return lay(d, InkWashLayers.WASH, (x, y) -> x >= 150 && x < 175 ? 70 : x >= 300 && x < 320 ? 225 : 255, 1, ToneDocument.KIND_PAINT);
    }
    /** Whether page pixel {@code i} was at mid grey or darker, as found under the stroke's pair: Behind takes nothing there. */
    static boolean marked(int[] found, int i) { return ModularCompose.blank(found[i]) == 0; }
    static void behind() {
        for (PartChoice life : new PartChoice[]{PartChoice.FAST_WET, PartChoice.NONE, PartChoice.DRYING}) for (String code : new String[]{WASH_BEHIND, code("paint-behind")}) {
            ModularBrush b = life(brush(code), life);
            if (life == PartChoice.DRYING) b.part(BrushPart.LIFE).set(PartDial.SETTLE, .6);
            ToneDocument d = behindPage();
            int[] found = shown(d);
            byte[][] before = all(d);
            live(d, b, 60, .8, null, wave());
            byte[][] after = all(d);
            int kept = 0, laid = 0;
            for (int i = 0; i < W * H; i++) {
                boolean same = true;
                for (int k = 0; k < after.length; k++) same &= before[k][i] == after[k][i];
                if (marked(found, i)) { check(same, b.choice(BrushPart.MEDIUM) + " " + life + " Behind leaves a mark byte for byte at " + i % W + ", " + i / W); kept++; }
                else if (!same) laid++;
            }
            check(same(Arrays.copyOfRange(before, 3, 6), Arrays.copyOfRange(after, 3, 6)), life + ": the ink layer is as it was");
            check(kept > 5000 && laid > 3000, b.choice(BrushPart.MEDIUM) + " " + life + ": Behind lays on blank paper (" + laid + " px) and leaves every mark (" + kept + " px)");
        }
        // On blank paper Behind is the medium's own way, every byte.
        for (String code : new String[]{WASH_BEHIND, code("paint-behind")}) {
            ToneDocument a = InkWashLayers.blank(W, H), o = InkWashLayers.blank(W, H);
            live(a, brush(code), 60, 1, null, wave()); live(o, blend(brush(code), 0), 60, 1, null, wave());
            check(same(all(a), all(o)), code + ": on blank paper as the medium's own way");
        }
        // The lab's rule on the app's own wash (newmods.js composeStroke): the wash layer goes from what it was found as
        // toward the wash laid its own way by how blank the page was (smoothstep(.55, .96, tone / paper)).
        ToneDocument own = behindPage(), back = behindPage();
        int[] found = shown(own), wash0 = layerTones(own, InkWashLayers.WASH);
        live(own, blend(brush(WASH_BEHIND), 0), 60, 1, null, wave()); live(back, brush(WASH_BEHIND), 60, 1, null, wave());
        int[] washOwn = layerTones(own, InkWashLayers.WASH), washBack = layerTones(back, InkWashLayers.WASH);
        int worst = 0, n = 0;
        for (int i = 0; i < W * H; i++) {
            double want = wash0[i] + (washOwn[i] - wash0[i]) * ModularCompose.blank(found[i]);
            worst = Math.max(worst, (int) Math.abs(Math.round(want) - washBack[i]));
            if (washOwn[i] != wash0[i]) n++;
        }
        check(n > 10000 && worst <= 1, "Behind is the lab's rule on the app's own wash, to a grey step: " + worst + " over " + n + " px");
    }
    /** Layer {@code layer}'s tones over white. */
    static int[] layerTones(ToneDocument d, int layer) {
        int[] out = new int[W * H];
        d.layerTones(layer, layer, java.util.stream.IntStream.range(0, W * H).toArray(), W * H, out);
        return out;
    }

    // ---- 4. White ----

    static void white() {
        // A white gel pen over a dark wash, on the ink layer: white where it crosses, nothing on paper.
        ToneDocument d = page(InkWashLayers.WASH, (x, y) -> x >= 100 && x < 380 ? 40 : 255);
        int[] before = shown(d);
        live(d, brush(WHITE_GEL), 8, 1, null, across(20, 460, 180));
        int[] after = shown(d);
        int paperChanged = 0, lit = 0;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            int i = y * W + x;
            if (before[i] == 255 && after[i] != 255) paperChanged++;
            if (x >= 110 && x < 370 && after[i] >= 240) lit++;
        }
        check(after[180 * W + 240] >= 245 && lit > 1500, "White over a dark wash is near white: " + after[180 * W + 240] + ", " + lit + " px");
        check(paperChanged == 0, "White on blank paper lays nothing: " + paperChanged + " px");
        check(d.layerDarkness(InkWashLayers.WASH, 0, 0, W, H, new int[W * H]) && layerTones(d, InkWashLayers.WASH)[180 * W + 240] == 40, "the wash under it is as it was: the ink layer covers it");
        // At Light the white is about half: over the dark wash it lands mid grey.
        ToneDocument half = page(InkWashLayers.WASH, (x, y) -> x >= 100 && x < 380 ? 40 : 255);
        live(half, brush(WHITE_GEL), 8, .5, null, across(20, 460, 180));
        check(Math.abs(half.compositeTone(240, 180) - (40 + 215 * .5)) < 20, "a Light white gel line is half white: " + half.compositeTone(240, 180));
        // A white wash on the wash layer lightens the wash, not the ink above it.
        ToneDocument both = lay(page(InkWashLayers.WASH, (x, y) -> 60), InkWashLayers.INK, (x, y) -> x >= 200 && x < 210 ? 0 : 255, 1, ToneDocument.KIND_PEN);
        ModularBrush ww = blend(brush(WASH_BEHIND), 1);
        live(both, ww, 60, 1, null, across(20, 460, 180));
        check(both.compositeTone(120, 180) > 90 && both.compositeTone(205, 180) == 0, "A white wash lightens the wash (" + both.compositeTone(120, 180) + ") but not the ink above it");
        // The lab's rule on the app's own chalk (newmods.js): the page toward white by the mark's darkness on blank paper.
        ruleOnPaper(blend(brush(CHALK), 0), brush(CHALK), 30, true);
        ruleOnPaper(blend(brush(WASH_BEHIND), 0), blend(brush(WASH_BEHIND), 1), 60, true);
    }
    /**
     * The lab's White (or Invert) rule against the app's own mark: {@code own} drawn on blank paper gives each pixel's
     * darkness a; {@code b} over the ground ({@link #mark} "ground") must give u + (255 − u) · a (Invert: u + (255 − 2u) · a).
     */
    static void ruleOnPaper(ModularBrush own, ModularBrush b, double size, boolean white) {
        ToneDocument paper = InkWashLayers.blank(W, H), d = page(InkWashLayers.WASH, (x, y) -> mark("ground", x, y));
        int[] u = shown(d);
        live(paper, own, size, 1, null, wave()); live(d, b, size, 1, null, wave());
        int[] a = shown(paper), got = shown(d);
        int worst = 0, n = 0;
        for (int i = 0; i < W * H; i++) {
            double k = 1 - a[i] / 255.0;
            if (k <= 0) continue;
            double want = white ? u[i] + (255 - u[i]) * k : u[i] + (255 - 2 * u[i]) * k;
            worst = Math.max(worst, (int) Math.abs(Math.round(want) - got[i]));
            n++;
        }
        check(n > 2000 && worst <= 2, b.choice(BrushPart.MEDIUM) + (white ? " White" : " Invert") + " is the lab's rule on the app's own mark, to two grey steps: " + worst + " over " + n + " px");
    }

    // ---- 5. Invert ----

    static void invert() {
        // Paper turns black, black turns white (the lab's check 5).
        ToneDocument d = page(InkWashLayers.WASH, (x, y) -> x >= 200 && x < 280 ? 34 : 255);
        live(d, life(brush(INVERT_INK), PartChoice.NONE), 30, 1, null, across(20, 460, 180));
        check(d.compositeTone(60, 180) <= 34 && d.compositeTone(240, 180) >= 204, "Invert: paper turns black (" + d.compositeTone(60, 180) + "), black turns white (" + d.compositeTone(240, 180) + ")");
        // Twice gives the page back: where both passes lay the full mark, the tone found; untouched pixels as they were.
        ToneDocument t = page(InkWashLayers.WASH, (x, y) -> mark("lines", x, y));
        int[] v0 = shown(t);
        ModularBrush inv = life(brush(INVERT_INK), PartChoice.NONE);
        live(t, inv, 30, 1, null, across(20, 460, 180));
        int[] v1 = shown(t);
        live(t, inv, 30, 1, null, across(20, 460, 180));
        int[] v2 = shown(t);
        int full = 0, worst = 0, moved = 0;
        for (int i = 0; i < W * H; i++) {
            if (v1[i] == v0[i] && v2[i] != v0[i] && Math.abs(255 - 2 * v0[i]) > 2) moved++;
            if (v1[i] == 255 - v0[i]) { full++; worst = Math.max(worst, Math.abs(v2[i] - v0[i])); }
            else if (v1[i] == v0[i] && v2[i] != v0[i]) moved++;
        }
        check(full > 5000 && worst <= 1 && moved == 0, "Invert twice gives the page back where it laid in full (" + full + " px, at most " + worst + " off), nothing else moved (" + moved + ")");
        // The lab's rule on the app's own wash.
        ruleOnPaper(blend(brush(WASH_BEHIND), 0), blend(brush(WASH_BEHIND), 3), 60, false);
        // Invert on the ink layer reads what shows under it: the wash below.
        ToneDocument pair = page(InkWashLayers.WASH, (x, y) -> x >= 200 && x < 280 ? 30 : 255);
        live(pair, life(brush(INVERT_INK), PartChoice.NONE), 30, 1, null, across(20, 460, 180));
        check(pair.compositeTone(240, 180) >= 215 && pair.compositeTone(60, 180) <= 30, "Invert ink turns the wash under it light, paper dark: " + pair.compositeTone(240, 180) + ", " + pair.compositeTone(60, 180));
    }

    // ---- 6. The eraser and Erasable ----

    static void erasable() {
        // Permanent ink and pencil on the ink layer; a white gel pen (ink: permanent) across both, then the eraser.
        ToneDocument d = lay(lay(InkWashLayers.blank(W, H), InkWashLayers.INK, (x, y) -> x >= 100 && x < 160 ? 0 : 255, 0), InkWashLayers.INK, (x, y) -> x >= 300 && x < 360 ? 60 : 255, 1);
        live(d, brush(WHITE_GEL), 10, 1, null, across(20, 460, 180));
        check(d.compositeTone(130, 180) >= 245 && d.compositeTone(330, 180) >= 245, "the white gel line over ink and pencil");
        ErasableChecks.eraseAll(d);
        check(d.compositeTone(130, 180) >= 245, "White over permanent ink stays when erased: " + d.compositeTone(130, 180));
        check(d.compositeTone(130, 60) == 0 && d.compositeTone(330, 60) == 255 && d.compositeTone(330, 180) == 255, "the ink stays, the pencil goes, and White over pencil leaves paper");
        // White chalk (charcoal, Erasable .9) over permanent ink: the chalk is on its own plane (kind layers, 2026-10-27), so
        // the eraser takes it off the ink, which shows again (but for the chalk's permanent tenth); nothing it leaves is
        // darker than the ink drawn there.
        ToneDocument c = lay(page(InkWashLayers.WASH, (x, y) -> 255), InkWashLayers.INK, (x, y) -> x >= 100 && x < 380 ? 30 : 255, 0);
        int[] inkOnly = shown(c);
        live(c, brush(CHALK), 30, 1, null, wave());
        int[] shownBefore = shown(c);
        ErasableChecks.eraseAll(c);
        int[] shownAfter = shown(c);
        int darker = 0, back = 0;
        for (int i = 0; i < W * H; i++) { if (shownAfter[i] < inkOnly[i]) darker++; if (shownAfter[i] < shownBefore[i]) back++; }
        check(darker == 0 && back > 1000, "The eraser takes White chalk off the ink, the ink showing again (" + back + " px), never darker than drawn: " + darker + " px");
        // Behind under permanent ink: erased, the ink is every byte as it was, the wash keeps its permanent share.
        ToneDocument w = lay(InkWashLayers.blank(W, H), InkWashLayers.INK, (x, y) -> mark("lines", x, y), 0);
        byte[][] ink0 = ErasableChecks.layer(w, InkWashLayers.INK);
        live(w, life(brush(WASH_BEHIND), PartChoice.NONE), 60, 1, null, wave());
        long dark0 = darkness(w, InkWashLayers.WASH);
        ErasableChecks.eraseAll(w);
        double keeps = darkness(w, InkWashLayers.WASH) / (double) dark0;
        check(ErasableChecks.same(ErasableChecks.layer(w, InkWashLayers.INK), ink0) && Math.abs(keeps - .6) < .04, "Behind under permanent ink: erased, the ink as it was, the wash keeps " + keeps);
        // Invert ink (permanent) over paper stays black when erased.
        ToneDocument v = InkWashLayers.blank(W, H);
        live(v, life(brush(INVERT_INK), PartChoice.NONE), 20, 1, null, across(20, 460, 180));
        int black = v.compositeTone(240, 180);
        ErasableChecks.eraseAll(v);
        check(black <= 5 && v.compositeTone(240, 180) == black, "Invert ink over paper is permanent ink: " + black + " stays " + v.compositeTone(240, 180));
        // An erasable white (White pencil: Graphite, Erasable 1) over erasable pencil: erased, paper.
        ToneDocument p = lay(InkWashLayers.blank(W, H), InkWashLayers.INK, (x, y) -> x >= 100 && x < 380 ? 80 : 255, 1);
        ModularBrush pencil = brush("hb.M40i000000000at_4e551zz1_0_000_1jz_0000000000000000000nz0.pg5zxt");
        live(p, pencil, 20, 1, null, across(20, 460, 180));
        ErasableChecks.eraseAll(p);
        check(p.compositeTone(240, 180) == 255 && p.compositeTone(240, 60) == 255, "White pencil over pencil, erased: paper");
    }
    /** Layer {@code layer}'s darkness over white, summed. */
    static long darkness(ToneDocument d, int layer) { long s = 0; for (int t : layerTones(d, layer)) s += 255 - t; return s; }

    // ---- 7. Smudge, Slow, gravity, Float ----

    static void smudge() {
        // A white smear over a dark band: the smudge moves the page as found, then its own white meets it.
        ToneDocument d = page(InkWashLayers.WASH, (x, y) -> x >= 150 && x < 330 ? 50 : 255);
        ModularBrush smear = brush(WHITE_GEL);
        smear.set(BrushPart.CARRY, new ModularBrush.Part(PartChoice.SMUDGE));
        smear.part(BrushPart.CARRY).set(PartDial.LOAD, 1);
        live(d, smear, 16, 1, null, across(20, 460, 180));
        check(d.compositeTone(240, 180) > 200, "A white smear lays its white over what it smudges: " + d.compositeTone(240, 180));
        // A wash behind that drags: on blank paper and against ink it draws, the ink layer untouched.
        ToneDocument e = behindPage();
        byte[][] ink0 = ErasableChecks.layer(e, InkWashLayers.INK);
        ModularBrush drag = brush(WASH_BEHIND);
        drag.set(BrushPart.CARRY, new ModularBrush.Part(PartChoice.DRAG));
        live(e, drag, 60, 1, null, wave());
        check(ErasableChecks.same(ErasableChecks.layer(e, InkWashLayers.INK), ink0), "A dragging wash behind leaves the ink layer");
    }
    static void drying() {
        // Slow: Behind settles only where it landed; White and Invert lay as Slow lays and nothing settles after.
        ModularBrush slow = life(brush(WASH_BEHIND), PartChoice.DRYING);
        slow.part(BrushPart.LIFE).set(PartDial.SETTLE, .8);
        WatercolorSession session = new WatercolorSession();
        ToneDocument d = behindPage();
        int[] found = shown(d);
        byte[][] before = all(d);
        live(d, slow, 60, 1, session, wave());
        session.settleNow(d);
        byte[][] after = all(d);
        int moved = 0;
        for (int i = 0; i < W * H; i++) if (marked(found, i)) for (int k = 0; k < after.length; k++) if (before[k][i] != after[k][i]) { moved++; break; }
        check(moved == 0, "A Slow wash behind settles only where it landed: " + moved + " marked px changed");
        for (int o : new int[]{1, 3}) {
            ModularBrush b = blend(slow, o);
            WatercolorSession s = new WatercolorSession();
            ToneDocument x = page(InkWashLayers.WASH, (px, py) -> 60), y = page(InkWashLayers.WASH, (px, py) -> 60);
            live(x, b, 60, 1, s, wave());
            check(!s.wet(), "White and Invert leave nothing settling: " + o);
            live(y, life(b, PartChoice.NONE), 60, 1, null, wave());
            check(x.compositeTone(240, 180) != 60, "a Slow white or inverted wash draws: " + o);
        }
        // Float: Blend does nothing on water: a White brush floats as its own way.
        ToneDocument f = InkWashLayers.blank(W, H), g = InkWashLayers.blank(W, H);
        live(f, life(brush(WHITE_GEL), PartChoice.FLOAT), 12, 1, null, wave()); live(g, life(blend(brush(WHITE_GEL), 0), PartChoice.FLOAT), 12, 1, null, wave());
        check(same(all(f), all(g)) && ErasableChecks.dark(f) > 0, "On water White floats as ink does");
    }

    // ---- 8. quick strokes end as drawn live ----

    static void quick() {
        String[] codes = {WHITE_GEL, WASH_BEHIND, INVERT_INK, code("paint-behind"), code("white-paint"), CHALK};
        int n = 0;
        for (String c : codes) for (PartChoice life : new PartChoice[]{PartChoice.NONE, PartChoice.FAST_WET, PartChoice.DRYING}) for (double size : new double[]{40, 160}) {
            ModularBrush b = life(brush(c), life);
            if (life == PartChoice.DRYING) b.part(BrushPart.LIFE).set(PartDial.SETTLE, .5);
            ToneDocument q = behindPage(), l = behindPage(), once = behindPage();
            quick(q, b, size, 1, null, wave());
            live(l, b, size, 1, null, wave());
            String what = c.substring(0, c.indexOf('.')) + " " + life + " " + size;
            if (life != PartChoice.FAST_WET) check(same(all(q), all(l)), what + ": quick ends as drawn live, every layer");
            else {
                check(Arrays.equals(shown(q), shown(l)), what + ": quick shows as drawn live");
                feedOnce(ModularStroke.start(once, b, size, 1, null), wave());
                check(same(all(q), all(once)), what + ": every layer as drawn live and composed once");
            }
            n++;
        }
        check(n == 36, "every case: " + n);
        // The preview under the pen: White shows white over the dark wash before the render lands.
        ToneDocument d = page(InkWashLayers.WASH, (x, y) -> 40);
        QuickStroke s = QuickStroke.quickly(d, brush(WHITE_GEL), 30, 1, null);
        double[][] p = across(20, 460, 180);
        for (int i = 0; i < p.length / 2; i++) s.sample((float) p[i][0], (float) p[i][1], ToolSettings.pressure((float) p[i][2]), 0, 0, (long) p[i][3]);
        s.present();
        check(d.compositeTone(100, 180) >= 240, "the quick preview shows the white gel line white over the dark wash: " + d.compositeTone(100, 180));
        s.finish();
        ToneDocument b2 = behindPage();
        QuickStroke s2 = QuickStroke.quickly(b2, brush(WASH_BEHIND), 120, 1, null);
        for (int i = 0; i < p.length; i++) s2.sample((float) p[i][0], (float) p[i][1], ToolSettings.pressure((float) p[i][2]), 0, 0, (long) p[i][3]);
        s2.present();
        check(layerTones(b2, InkWashLayers.WASH)[180 * W + 160] == 70 && layerTones(b2, InkWashLayers.WASH)[180 * W + 60] < 255, "the preview of a wash behind leaves the marks and lays on paper");
        s2.finish();
    }

    /** The whole path, then one present and the finish: composed once. */
    static void feedOnce(DrawingStroke s, double[][] path) {
        for (double[] p : path) s.sample((float) p[0], (float) p[1], ToolSettings.pressure((float) p[2]), 0, 0, (long) p[3]);
        s.present();
        s.finish();
    }

    // ---- 9. several cores ----

    static void cores() {
        for (String c : new String[]{WASH_BEHIND, INVERT_INK, WHITE_GEL}) {
            String[] crc = new String[2];
            for (int i = 0; i < 2; i++) {
                boolean was = Cores.shared;
                Cores.shared = i == 0;
                try { ToneDocument d = behindPage(); live(d, brush(c), 300, 1, null, wave()); crc[i] = LegacyConversionChecks.crc(d); }
                finally { Cores.shared = was; }
            }
            check(crc[0].equals(crc[1]), c + " at 300 px draws the same on several cores as on one");
        }
    }

    // ---- 10. rolls ----

    static void rolls() {
        int[][] got = new int[ModularStyle.all().size()][4];
        int rolls = 0, other = 0;
        for (ModularStyle s : ModularStyle.all()) for (int i = 0; i < 600; i++) {
            ModularBrush with = s.roll(new Random(5000L * (s.index + 1) + i)), without;
            ModularStyle.blendRolls = false;
            try { without = s.roll(new Random(5000L * (s.index + 1) + i)); } finally { ModularStyle.blendRolls = true; }
            // A roll that Resist or Screentone takes has no Blend (ResistChecks and ScreenChecks count those).
            if (with.choice(BrushPart.MEDIUM) == PartChoice.RESIST || with.choice(BrushPart.MEDIUM) == PartChoice.TONE) continue;
            int o = (int) with.part(BrushPart.MEDIUM).get(PartDial.M_BLEND);
            got[s.index][o]++;
            check((int) without.part(BrushPart.MEDIUM).get(PartDial.M_BLEND) == 0, "without Blend's rolls none has one");
            // The name may say its Blend ("tidy-chalky-pen", BrushNames.described): the code's parts and seed are compared.
            if (!ModularCode.settings(blend(with, 0)).equals(ModularCode.settings(without)) || !with.name.equals(BrushNames.name(with))) other++;
            rolls++;
        }
        check(other == 0, "every roll is as before but for its Blend: " + other + " of " + rolls);
        for (ModularStyle s : ModularStyle.all()) {
            int[] g = got[s.index];
            int[] odds = {0, s.whiteOdds(), s.behindOdds(), s.invertOdds()};
            for (int o = 1; o < 4; o++) {
                if (odds[o] == 0) { check(g[o] == 0, s.name + " never rolls Blend " + o); continue; }
                double want = 600.0 / odds[o];
                check(g[o] >= want / 3 && g[o] <= want * 2.5, s.name + " rolls Blend " + o + " about one in " + odds[o] + ": " + g[o] + " of 600");
            }
        }
        check(ModularStyle.named("Pastels").whiteOdds() == 30 && ModularStyle.named("Watercolor").behindOdds() == 30 && ModularStyle.named(ModularStyle.ANYTHING_GOES).invertOdds() == 80, "The lab's odds");
        // A locked medium keeps its Blend; the medium's die rolls it back to its own way; variations never give one.
        ModularBrush w = brush(WHITE_GEL);
        ModularLocks locks = new ModularLocks();
        locks.parts.add(BrushPart.MEDIUM);
        check((int) w.style.roll(new Random(3), w, locks).part(BrushPart.MEDIUM).get(PartDial.M_BLEND) == 1, "a locked medium keeps White");
        ModularBrush plain = ModularStyle.named("Fineliner").roll(new Random(11));
        for (int i = 0; i < 50; i++) for (ModularVariations.Made m : ModularVariations.make(plain, new ModularLocks(), new Random(i)))
            check(m.brush.part(BrushPart.MEDIUM).get(PartDial.M_BLEND) == 0, "a variation never gives a Blend");
    }

    // ---- 11. words; 12. Brush Lab ----

    /** The lab's words for a brush (newmods.js on the same codes): its code, Medium's card, "This brush", caption. */
    static final String[][] LAB_WORDS = {
            {WHITE_GEL, "White ink", "White ink line. Permanent.", "Fineliner, white, permanent"},
            {WASH_BEHIND, "Wash behind", "Wet wash. Goes behind marks. Part erasable.", "Watercolour, behind, part erasable"},
            {INVERT_INK, "Invert ink", "Ink line. Inverts what's under it. Permanent.", "Fineliner, invert, permanent"},
            {"paint-behind.M80i000000000at_2zz00at2_0_000_1jz_0000000000000000000nz0.13ztx3e", "Paint behind", "Gouache. Goes behind marks. Part erasable.", "Gouache, behind, part erasable"},
            {"white-paint.M80i000000000at_2zz00at1_0_000_1jz_0000000000000000000nz0.13ztx3e", "White paint", "White gouache. Part erasable.", "Gouache, white, part erasable"},
            {CHALK, "Chalk", "White chalk on cold-press paper. Soft speckled edge. Part erasable.", "Charcoal, white, round, part erasable"},
            {"hb.M40i000000000at_4e551zz1_0_1v5_1jz_8arsr00dwjs00000000nz0.pg5zxt", "White pencil", "White pencil on cold-press paper. Picks up the texture. Pressure darkens, tilt lightens.", "Pencil, white, HB"},
            {"wb.M70i000000000at_1ulat00770000ee2_0_000_2jz_0000000000000000000nz0.11kx", "Wash behind", "Wet wash. Goes behind marks. Part erasable. Wet for 6.7 s.", "Ink wash, behind, wet wash, part erasable, slow"},
    };
    static void words() {
        for (String[] w : LAB_WORDS) {
            ModularBrush b = brush(w[0]);
            check(ModularWords.choiceWords(b, BrushPart.MEDIUM).equals(w[1]), "Medium's card says \"" + w[1] + "\": " + ModularWords.choiceWords(b, BrushPart.MEDIUM));
            check(ModularWords.plainWords(b).equals(w[2]), "This brush says \"" + w[2] + "\": " + ModularWords.plainWords(b));
            check(String.join(", ", ModularWords.caption(b)).equals(w[3]), "the caption says \"" + w[3] + "\": " + ModularWords.caption(b));
        }
        check(ModularWords.swapNote(brush(WHITE_GEL), BrushPart.MEDIUM).equals("White ink"), "the swap tile's note");
    }
    static void lab() {
        ModularBrush.Part ink = brush(WHITE_GEL).part(BrushPart.MEDIUM), cover = brush(code("white-paint")).part(BrushPart.MEDIUM);
        check(PartDial.M_BLEND.shown(1, ink).equals("White") && PartDial.M_BLEND.shown(0, ink).equals("Darken") && PartDial.M_BLEND.shown(0, cover).equals("Cover"), "Blend Mode's options: Darken, or Opaque's Cover, then White, Behind, Invert");
        java.util.List<PartDial> dials = ink.choice.dials;
        // Fling last of all (FlickFlingChecks).
        int n = dials.size() - 1 - PartDial.SCREEN_DIALS.length;
        check(dials.get(n - 1) == PartDial.M_BLEND && dials.get(n - 2) == PartDial.ERASABLE && dials.subList(n, dials.size() - 1).equals(java.util.Arrays.asList(PartDial.SCREEN_DIALS))
                && dials.get(dials.size() - 1) == PartDial.FLING, "Blend after Erasable on every medium, then only Screen's (ScreenChecks), then Fling");
        java.util.Map<String, String> store = new java.util.HashMap<>();
        BrushLibrary.Store st = new BrushLibrary.Store() { public String get(String k) { return store.get(k); } public void put(String k, String v) { if (v == null) store.remove(k); else store.put(k, v); } };
        BrushLab lab = new BrushLab(new ToolSlots(st, new Random(1)), 2, new Random(7));
        lab.put(Brush.of(brush(WHITE_GEL)));
        check(lab.active(PartDial.M_BLEND), "Blend is live on a brush on paper");
        lab.choose(BrushPart.LIFE, PartChoice.FLOAT);
        check(!lab.active(PartDial.M_BLEND), "and greyed on Float, where it does nothing");
        LabLayout manta = new LabLayout(1024, 1365), nomad = new LabLayout(749, 998);
        check(!manta.menu(PartDial.M_BLEND.choices) && nomad.menu(PartDial.M_BLEND.choices) && !manta.menu(PartDial.LINES.choices), "Blend's four options: segments on the Manta, a menu button on the Nomad (Line style too there, since 2026-10-23)");
        // A White brush's samples show on a dark ground; its plain twin's on paper.
        int[] w = WildSample.part(brush(WHITE_GEL), BrushPart.MEDIUM, 200, 120), o = WildSample.part(blend(brush(WHITE_GEL), 0), BrushPart.MEDIUM, 200, 120);
        check(darkShare(w) > .5 && darkShare(o) < .3, "A White brush's card wave draws on a dark ground: " + darkShare(w) + " dark, its plain twin " + darkShare(o));
    }
    /** The share of {@code pixels} darker than mid grey. */
    static double darkShare(int[] pixels) { int n = 0; for (int p : pixels) if ((p & 255) < 128) n++; return n / (double) pixels.length; }
}
