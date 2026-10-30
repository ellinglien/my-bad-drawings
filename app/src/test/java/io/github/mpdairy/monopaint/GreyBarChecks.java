package io.github.mpdairy.monopaint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The grey bar (the owner, 2026-10-19: "a gradient displayed like in monopaint in the sizes and greys selector"):
 * MonoPaint's color bar strip ({@link ShadeStrip}) for the grey swatches, its place from black to white and back
 * ({@link ToolSwatches#darkness}, {@link ToolSwatches#tone}), what was missing black, and the Sizes and Greys window's
 * and the grey flyout's fit ({@link SwatchSetLayout}).
 */
public final class GreyBarChecks {
    /** The Manta's and the Nomad's pixels a dp (1920 px over 1024 dp; 1404 over 749). */
    static final double DENSITY = 1.875;

    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        strip(); mapping(); swatches(); black(); words(); fit(); sideStrip();
        System.out.println("PASS: the grey bar is MonoPaint's color bar strip, black at the left to white at the right in the panel's dots, a solid black and white end; "
                + "it holds the panel's " + ShadeStrip.LEVELS + " dot densities in order, each a share of the strip a pen can hit, and sets a grey swatch to one of them (darkness 1 − tone/255), "
                + "a tap at its marker picking the grey it marks; every swatch's grey has its marker on the bar; black and white are always its ends, whatever the brush "
                + "(washes showed no black: one glaze laid at most 34 %; since washes reach black a wash's black lays 97 %, solid black on the panel, and the 21 Basic tools (22 before Drips retired) that couldn't reach black draw near-black there); "
                + "the side bar's grey strip stands beside the four sizes in their column, a tap near a mark taking its grey and elsewhere setting the nearest mark's; "
                + "the Sizes and Greys window fits the Manta and the Nomad either way up without scrolling, the bar beside the big grey under the faces, and so does the flyout");
    }

    /** The bar's strip on the tablets, px: its length and its end zones. */
    static int length() { return (int) Math.round((SwatchSetLayout.BAR - 2 * ShadeStrip.INSET) * DENSITY); }
    static int endpoint(int length) { return ShadeStrip.endpoint(length, (int) Math.round(ShadeStrip.END * DENSITY)); }

    /** MonoPaint's strip: black to white, all 65 densities in order, both ends solid, each level a share a pen can hit. */
    static void strip() {
        int length = length(), end = endpoint(length);
        check(ShadeStrip.toneAt(0, length, end) == 0 && ShadeStrip.toneAt(length - 1, length, end) == 255, "Black at the start, white at the end");
        for (int x = 0; x < end; x++)
            check(DotPattern.whiteCount(ShadeStrip.toneAt(x, length, end)) == 0 && DotPattern.whiteCount(ShadeStrip.toneAt(length - 1 - x, length, end)) == 64,
                    "The end zones solid black and solid white");
        Set<Integer> densities = new TreeSet<>();
        int before = -1, widest = 0, narrowest = Integer.MAX_VALUE, run = 0;
        for (int x = 0; x < length; x++) {
            int density = DotPattern.whiteCount(ShadeStrip.toneAt(x, length, end));
            check(density >= before, "Lighter along the strip, never darker, at " + x);
            if (density != before && before >= 0) { if (before > 0 && before < 64) { widest = Math.max(widest, run); narrowest = Math.min(narrowest, run); } run = 0; }
            run++; before = density;
            densities.add(density);
        }
        check(densities.size() == ShadeStrip.LEVELS, "Every one of the panel's " + ShadeStrip.LEVELS + " densities on the strip: " + densities.size());
        check(narrowest >= 4, "Each grey at least 4 px of the strip, about 2 dp: " + narrowest + " to " + widest + " px");
        int[] pixels = ShadeStrip.pixels(length, 8, end);
        check(pixels.length == length * 8 && pixels[0] == 0xff000000 && pixels[length - 1] == 0xffffffff, "Its pixels the panel's dots, black and white");
    }

    /** Position to grey and back: a pen on the marker picks the grey it marks; the levels' darkness round-trips. */
    static void mapping() {
        int length = length(), end = endpoint(length);
        Set<Integer> levels = new TreeSet<>();
        for (int x = 0; x < length; x++) levels.add(ShadeStrip.toneAt(x, length, end));
        check(levels.size() == ShadeStrip.LEVELS, "One tone a density: " + levels.size());
        for (int tone : levels) {
            double d = ToolSwatches.darkness(tone);
            check(d >= 0 && d <= 1 && ToolSwatches.tone(d) == tone, "A level's darkness keeps its tone: " + tone);
            float at = ShadeStrip.markerAt(tone, length, end);
            check(at >= 0 && at <= length, "Its marker on the strip: " + tone);
            int picked = ShadeStrip.toneAt(at, length, end);
            check(DotPattern.whiteCount(picked) == DotPattern.whiteCount(tone), "A tap on its marker picks it again: " + tone + " → " + picked);
        }
        check(ToolSwatches.darkness(0) == 1 && ToolSwatches.darkness(255) == 0 && ToolSwatches.tone(1) == 0 && ToolSwatches.tone(0) == 255
                && ToolSwatches.tone(2) == 0 && ToolSwatches.tone(-1) == 255, "Black is darkness 1, paper 0, either way, out-of-range darkness kept in it");
    }

    /** The swatches as kept: any darkness 0–1 shows at its density, its marker where that density is. */
    static void swatches() {
        int length = length(), end = endpoint(length);
        ToolSwatchesChecks.prefs = new HashMap<>();
        ToolSwatches s = new ToolSwatches(ToolSwatchesChecks.store());
        for (int i = 0; i < ToolSwatches.COUNT; i++) {
            int tone = ToolSwatches.tone(s.grey(i));
            float at = ShadeStrip.markerAt(tone, length, end);
            check(DotPattern.whiteCount(ShadeStrip.toneAt(at, length, end)) == DotPattern.whiteCount(tone), "Grey swatch " + i + "'s marker on its own density");
        }
        // A level picked on the bar is the tool's grey as it is, and read back the same.
        Brush pen = ToolSwatchesChecks.style("Fineliner", 1);
        double light = ToolSwatches.darkness(ShadeStrip.toneAt(length / 2f, length, end));
        s.useGrey("slot0", light);
        check(s.look("slot0", pen).darkness == light && new ToolSwatches(ToolSwatchesChecks.store()).look("slot0", pen).darkness == light, "A grey set on the bar kept as set: " + light);
        s.useGrey("slot0", ToolSwatches.darkness(ShadeStrip.toneAt(0, length, end)));
        check(s.look("slot0", pen).darkness == 1, "Black at the bar's start sets the grey black");
    }

    /**
     * The Basic tools that couldn't reach black (the owner, 2026-10-19: "doesn't include black for some"): the washes,
     * whose swatch shows its glaze, then a third of its darkness, so their darkest grey was 34 % grey.
     */
    static final String[] WITHOUT_BLACK = {"Soft wash", "Wet blooms", "Backrun", "Granulating", "Slow bloom", "Pooling wash", "Tilt blend",
            "Sinking grain", "Stipple sponge", "Wash behind", "Wash comb", "Dirty brush", "Water brush", "Grey wash", "Flat wash", "Dry glaze", "Grainy wash",
            "Wet hake", "Rough hake", "Flat hake", "Big sky"};
    /**
     * What lacked black: the 22 washes (21 on Basic tools since Drips retired, 2026-10-22) ({@link #WITHOUT_BLACK}), whose darkest grey was a 34 % glaze. The owner chose to
     * let washes reach black (2026-10-19): a wash's black lays {@link ModularSizes#WASH_BLACK}, 97 %, which the panel
     * shows solid black, so every Basic tool's swatch reaches black; one kept from before shows its 34 % as it did
     * ({@link ModularSizes#sinceBlack}). Each of them that lays paint of its own lays near-black at black, most of its
     * stroke 85 % or more, darker at its pooled edges than its middle. The bar keeps black and white at its ends whatever
     * the brush, the big grey beside it showing what the brush lays.
     */
    static void black() {
        List<String> washes = new ArrayList<>(), dark = new ArrayList<>();
        int length = length(), end = endpoint(length);
        for (ToolPresets.Preset preset : ToolPresets.all()) {
            ModularBrush parts = preset.brush().parts;
            int darkest = ModularSizes.swatch(parts, 1);
            if (ModularSizes.washes(parts)) {
                // A wash added since (the Highlighter) never lacked black: only those are listed.
                boolean before = java.util.Arrays.asList(WITHOUT_BLACK).contains(preset.name);
                if (before) washes.add(preset.name);
                check(darkest == Math.round(255 * (1 - ModularSizes.WASH_BLACK)) && DotPattern.whiteCount(darkest) == 0
                                && ModularSizes.swatch(parts, ModularSizes.sinceBlack(parts, 1)) == Math.round(255 * (1 - ModularSizes.GLAZE)),
                        "A wash's black is a 97 % glaze, solid black on the panel; kept from before, its 34 %: " + preset.name + " " + darkest);
                String laid = before ? laidAtBlack(parts) : null;
                if (laid != null) dark.add(preset.name + " " + laid);
            } else check(darkest == 0, "Every other Basic tool's darkest is black: " + preset.name + " " + darkest);
            check(ModularSizes.swatch(parts, ToolSwatches.darkness(ShadeStrip.toneAt(0, length, end))) == darkest
                    && ModularSizes.swatch(parts, ToolSwatches.darkness(ShadeStrip.toneAt(length - 1, length, end))) == 255,
                    "The bar's ends are the brush's darkest and paper: " + preset.name);
        }
        check(washes.equals(java.util.Arrays.asList(WITHOUT_BLACK)), "The 21 that couldn't reach black are the washes: " + washes);
        System.out.println("  The 21 Basic tools that couldn't reach black (a 34 % glaze at most), at black now (the stroke's median, its darkest twentieth; "
                + (washes.size() - dark.size()) + " smudges lay no paint of their own): " + String.join(", ", dark));
    }
    /**
     * A wash Basic tool's stroke at black on blank paper, 60 px, beside the same at the old black (its 34 % glaze): the
     * median darkness of what it lays, checked near-black (85 % or more), its darkest twentieth darker than its middle
     * (pooled edges, grain) where the old one's was, and as many greys in it as the old one had, or more (its blooms,
     * edges and grain not flattened; a wet watercolour, dried back first, at least 80 and two fifths of them); "median/darkest twentieth %", or null for a smudge that lays no paint of its own
     * on blank paper.
     */
    static String laidAtBlack(ModularBrush b) {
        double[] now = strokeAt(b, 1), was = strokeAt(b, ModularSizes.sinceBlack(b, 1));
        if (now == null) return null;
        check(now[0] >= .85, "Near-black at black: " + Math.round(100 * now[0]) + " %");
        check(now[1] > now[0] || !(was[1] > was[0]), "its darkest twentieth darker than its middle, as at the old black: " + Math.round(100 * now[0]) + " %, " + Math.round(100 * now[1]) + " %");
        // A wet watercolour (the wet paint spec, section 9) dries back by multiplying its pigment (dark edge, grain, blooms):
        // near black that saturates, so it keeps fewer greys there, though still dozens of them.
        boolean wet = b.choice(BrushPart.LIFE) == PartChoice.WET_PAINT;
        check(wet ? now[2] >= 80 && now[2] >= was[2] / 2.5 : now[2] >= was[2], "and as many greys as at the old black: " + now[2] + ", was " + was[2]);
        return Math.round(100 * now[0]) + "/" + Math.round(100 * now[1]) + " %";
    }
    /** {@link #laidAtBlack}'s stroke at {@code darkness}: its median darkness, its darkest twentieth's and how many greys it lays; null for none. */
    static double[] strokeAt(ModularBrush b, double darkness) {
        ToneDocument d = InkWashLayers.blank(360, 160);
        WatercolorSession session = new WatercolorSession();
        DrawingStroke s = QuickStroke.start(d, b, 60, darkness, session);
        for (int i = 0; i <= 100; i++) {
            double t = i / 100.0;
            s.sample((float) (40 + 280 * t), (float) (80 + 30 * Math.sin(2 * Math.PI * t)), ToolSettings.pressure(.7f), 0, 0, i * 6L);
            s.present();
        }
        s.finish();
        // Wet paint dries back first, as on the page before the droplet's tap (the wet paint spec, section 9).
        if (ResolvedBrush.of(b, 60, darkness).wet != null) while (session.tickDrying(d)) { }
        session.settleNow(d);
        double[] v = new double[d.width * d.height];
        Set<Integer> greys = new TreeSet<>();
        int n = 0;
        for (int y = 0; y < d.height; y++) for (int x = 0; x < d.width; x++) { int t = d.compositeTone(x, y); if (t < 250) { v[n++] = 1 - t / 255.0; greys.add(t); } }
        if (n < 500) return null;
        v = java.util.Arrays.copyOf(v, n);
        java.util.Arrays.sort(v);
        return new double[]{v[n / 2], v[n - 1 - n / 20], greys.size()};
    }

    /** The grey in words: black, white, a percentage; a wash's glaze first. */
    static void words() {
        check(ToolSwatches.greyWords(1, 0).equals("Black") && ToolSwatches.greyWords(0, 255).equals("White")
                && ToolSwatches.greyWords(ToolSwatches.darkness(120), 120).equals("53% grey"), "Ink's greys: Black, White, 53% grey");
        // A wash's black lays 97 %, which the panel shows solid black: "Black". Half way, half that; kept from before, its 34 %.
        ModularBrush wash = ToolPresets.all().stream().filter(p -> p.name.equals("Soft wash")).findFirst().get().brush().parts;
        String black = ToolSwatches.greyWords(1, ModularSizes.swatch(wash, 1)), half = ToolSwatches.greyWords(.5, ModularSizes.swatch(wash, .5)),
                kept = ToolSwatches.greyWords(ModularSizes.sinceBlack(wash, 1), ModularSizes.swatch(wash, ModularSizes.sinceBlack(wash, 1)));
        check(black.equals("Black") && half.equals("50% glaze, 49% grey") && kept.equals("35% glaze, 34% grey"), "A wash's: " + black + "; " + half + "; " + kept);
        check(ToolSwatches.greyWords(ToolSwatches.darkness(254), 254).equals("1% grey") && ToolSwatches.greyWords(ToolSwatches.darkness(1), 1).equals("99% grey"),
                "Never 0 % or 100 %: those are White and Black");
        int room = SwatchSetLayout.inside(SwatchSetLayout.WIDTH);
        for (int tone = 0; tone < 256; tone++)
            for (String w : new String[]{ToolSwatches.greyWords(ToolSwatches.darkness(tone), tone), ToolSwatches.greyWords(ToolSwatches.darkness(tone), (int) Math.round(255 * (1 - ModularSizes.WASH_BLACK * ToolSwatches.darkness(tone)))),
                    ToolSwatches.greyWords(ToolSwatches.darkness(tone), (int) Math.round(255 * (1 - ModularSizes.GLAZE * ToolSwatches.darkness(tone))))})
                check(LabText.width(w, TypeStyle.CAPTION.sp, 0) <= room && LabText.line(TypeStyle.CAPTION.sp) <= SwatchSetLayout.VALUE, "Words on one line: " + w);
    }

    /** The window and the flyout on the four screens: whole, no scrolling, the bar under the grey faces beside the big grey. */
    static void fit() {
        StringBuilder heights = new StringBuilder();
        for (int[] screen : DrawLayoutChecks.SCREENS) {
            int w = screen[0], h = screen[1], set = SwatchSetLayout.set(w, true);
            check(SwatchSetLayout.fits(w, h), "The Sizes and Greys window and the flyout fit " + w + "x" + h + ": " + set + " dp tall");
            check(SwatchSetLayout.width(w) == SwatchSetLayout.WIDTH, "At its full width on " + w + "x" + h);
            check(SwatchSetLayout.set(w, false) < set, "Sizes only (the smudge tool) shorter");
            heights.append(heights.length() == 0 ? "" : ", ").append(w).append("x").append(h).append(' ').append(set).append(" of ").append(h);
        }
        int inside = SwatchSetLayout.inside(SwatchSetLayout.WIDTH);
        check(SwatchSetLayout.BIG + SwatchSetLayout.BAR_GAP + SwatchSetLayout.BAR == inside && SwatchSetLayout.faceRow() <= inside,
                "The big grey and the bar fill the window's inside, the four faces in it: " + SwatchSetLayout.BAR + " dp of bar");
        check(SwatchSetLayout.BIG - DrawLayout.FACE_PAD * 2 > SwatchSetLayout.FACE, "The chosen grey bigger than a face");
        check(SwatchSetLayout.stopRows(inside) == 2, "The nine stops in two rows");
        // The window with the eight-grey grid it had (two rows of faces, 4 dp apart, 8 dp under the row): the bar is shorter.
        int grid = 8 + 2 * SwatchSetLayout.faceTall(SwatchSetLayout.FACE) + 4, bar = SwatchSetLayout.greyEditor();
        check(bar < grid, "The grey editor shorter than the grid of eight it replaces: " + bar + " dp, was " + grid);
        System.out.println("  Sizes and Greys window, dp: " + heights + "; the flyout " + SwatchSetLayout.flyoutWidth() + "x" + SwatchSetLayout.flyoutHeight());
    }

    /**
     * The side bar's grey strip ({@link GreyStrip}, the owner, 2026-10-21): as tall as the four faces' rows, as wide as
     * a grey face was, the grey in use marked; a tap anywhere picks the strip's grey there as the one current grey and
     * sets no swatch (since 2026-10-24): every slot draws at it, and the swatches are as they were.
     */
    static void sideStrip() {
        ToolSwatchesChecks.prefs = new HashMap<>();
        ToolSwatches s = new ToolSwatches(ToolSwatchesChecks.store());
        for (int[] screen : DrawLayoutChecks.SCREENS) {
            DrawLayout at = new DrawLayout(screen[0], screen[1]);
            // On the column's one grid (the column pass, 2026-10-23): the strip in the cell beside the sizes, from the first
            // size's square's top to the last's foot, exactly a square's width, as the swatches under it (the owner, 2026-10-23:
            // "make the shade boxes the same width and aligned with the grey picker"); the gaps the same both ways.
            int strip = at.faceSide();
            check(strip >= ShadeStrip.SIDE_WIDTH + 2 * ShadeStrip.SIDE_REACH, "The strip " + strip + " dp wide, a swatch's width, its tab beside it, at " + screen[0] + "x" + screen[1]);
            // The picker box's length less its gap, half as long again where it fits (2026-10-29, "more fine control"): each
            // grey 4 px or more of it on the Manta and the Nomad upright (2.9 and 2.6 at four rows, both turned since Shapes and Play's row, 2026-10-30), never under 2.5.
            int length = (int) Math.round((at.box - DrawLayout.FACE_GAP) * DENSITY), end = ShadeStrip.endpoint(length, (int) Math.round(ShadeStrip.END * DENSITY));
            double each = (length - 2.0 * end) / ShadeStrip.LEVELS;
            check(each >= 2.5 && (screen[0] > screen[1] || each >= 4), "Each grey " + each + " px of the strip at " + screen[0] + "x" + screen[1]);
            for (int i = 1; i < ToolSwatches.COUNT; i++)
                check(ShadeStrip.markerAt(ToolSwatches.tone(s.grey(i)), length, end) > ShadeStrip.markerAt(ToolSwatches.tone(s.grey(i - 1)), length, end), "The default greys darkest at the top of the strip: " + i);
        }
        double[] before = new double[ToolSwatches.COUNT];
        for (int i = 0; i < before.length; i++) before[i] = s.grey(i);
        s.useGrey("slot0", .3);
        for (int i = 0; i < before.length; i++) check(s.grey(i) == before[i], "A grey from the strip changes no swatch: " + i);
        Brush pen = ToolSwatchesChecks.style("Fineliner", 1);
        check(Math.abs(s.look("slot0", pen).darkness - .3) < 1e-9 && Math.abs(s.look("slot6", pen).darkness - .3) > 1e-9, "The slot draws at the strip's grey, another slot not");
    }

    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
