package io.github.mpdairy.monopaint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class WildSampleChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        loop(); tray(); sample(); kept(); neutral();
        System.out.println("PASS: samples follow the app icon's loop, turned to stand upright and its tail after the crossing shortened, as the mockup's loopPoints (light at the start, firm and slow from the turn to the end), or for the drawing screen's bar a pen in a tray (a short gentle stroke lying across its row, firm in from its left edge and ending where the drawings do, inside the row at any size up to the most it holds, a thin, sparse one drawn a little darker so it reads, a pale wash as drawn), at the slot's size and grey, drawn through the brush's real stroke in the page's dots, sized to the tile, kept by settings, darkness, size, shape and tile; "
                + "a slot's picture is drawn at full darkness whatever the current grey (the one grey, 2026-10-24), a brush that lays white light on a dark ground, every other on paper");
    }
    static void loop() {
        // The icon's loop turned a quarter clockwise and flipped left to right (x and y swapped), its tail after it
        // crosses itself half as long; light at the start, firm from .6 of the way to the end: the mockup's loopPoints.
        float[][] p = WildSample.loop(140, 200, 22, 0, 0);
        check(p.length == 190, "The mockup's 190 points for a 140 x 200 card: " + p.length);
        near(p[0], 49.16981f, 43.849056f, .15f, 2.45f, "first");
        near(p[50], 35.432194f, 124.81309f, .69788545f, 175.62395f, "fiftieth");
        near(p[189], 77.38847f, 156.2534f, .9f, 816.4146f, "last");
        for (int i = 1; i < p.length; i++) {
            check(Math.hypot(p[i][0] - p[i - 1][0], p[i][1] - p[i - 1][1]) >= .6, "Points under .6 px apart are left out");
            check(p[i][3] > p[i - 1][3], "Time runs on");
            check(p[i][2] >= p[i - 1][2], "Pressure only rises");
        }
        int firm = 0;
        for (float[] point : p) if (point[2] > .899f) firm++;
        check(firm > p.length / 3, "and stays firm to the end: " + firm + " of " + p.length);
        for (float[] point : p) check(point[0] >= 0 && point[0] <= 140 && point[1] >= 0 && point[1] <= 200, "The loop stays on the card");
        float[][] moved = WildSample.loop(140, 200, 22, 30, 40);
        check(Math.abs(moved[0][0] - p[0][0] - 30) < 1e-3 && Math.abs(moved[0][1] - p[0][1] - 40) < 1e-3, "and moves with its box");
        // It still crosses itself, once, and runs on past the crossing.
        int crossings = 0, last = -1;
        for (int k = 2; k < p.length; k++) for (int j = 0; j + 1 < k - 1; j++) if (crosses(p[j], p[j + 1], p[k - 1], p[k])) { crossings++; last = k; }
        check(crossings == 1 && p.length - last > 10, "The loop crosses itself once and its tail runs on past it: " + crossings + ", " + (p.length - last) + " points after");
        java.util.List<double[]> line = new java.util.ArrayList<>();
        for (int i = 0; i <= 100; i++) line.add(new double[]{i, i % 50 < 25 ? 0 : 1});
        check(WildSample.cut(line, .5) == line, "A line that never crosses itself is kept whole");
        float[][] wide = WildSample.loop(200, 140, 22, 0, 0);
        float x0 = 1e9f, x1 = -1e9f, y0 = 1e9f, y1 = -1e9f;
        for (float[] point : wide) { x0 = Math.min(x0, point[0]); x1 = Math.max(x1, point[0]); y0 = Math.min(y0, point[1]); y1 = Math.max(y1, point[1]); }
        check(y1 - y0 > 1.1 * (x1 - x0) && Math.abs(y1 - y0 - 96) < 1, "In a wide card the loop stands a little taller than wide, as high as it fits: " + (x1 - x0) + " x " + (y1 - y0));
        check(Math.abs(WildSample.reach(200, 140) - 140 * .73) < 1e-3 && WildSample.reach(72, 300) == 72 && WildSample.waveReach(300, 140) == 140,
                "A loop's reach is about its width as drawn; a wave's stays its tile's height");
    }
    private static boolean crosses(float[] a, float[] b, float[] c, float[] d) {
        double d1 = (d[0] - c[0]) * (a[1] - c[1]) - (d[1] - c[1]) * (a[0] - c[0]), d2 = (d[0] - c[0]) * (b[1] - c[1]) - (d[1] - c[1]) * (b[0] - c[0]);
        double d3 = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]), d4 = (b[0] - a[0]) * (d[1] - a[1]) - (b[1] - a[1]) * (d[0] - a[0]);
        return (d1 > 0) != (d2 > 0) && (d3 > 0) != (d4 > 0);
    }
    static void tray() {
        // The owner, 2026-10-27: as the drawn tools, in from the row's left edge already firm, ending where the drawings do.
        float[][] p = WildSample.tray(60, 20, 4, 2);
        int in = p.length - 61, last = p.length - 1;
        check(in > 0 && WildSample.trayLead(4) >= StrokeMath.taperLens(1, 0, 4)[0] + 2, "The pen goes down off the row, past the longest taper in: " + in + " points");
        near(p[0], -WildSample.trayLead(4), 12, .1f, 0, "tray stroke's first, off the row (light, a little below the middle)");
        near(p[in], 0, 12, .95f, p[in][3], "firm at the row's edge");
        near(p[last], DrawLayout.TIP_END * 60 - 2, 8, .95f, p[last][3], "tray stroke's last (a little above the middle), its mark ending where the drawings do");
        for (int i = 1; i < p.length; i++) {
            check(p[i][2] >= p[i - 1][2], "The tray stroke's pressure never falls");
            check(p[i][0] > p[i - 1][0], "It runs left to right");
        }
        for (float[] point : p) check(Math.abs(point[1] - 10) <= 2 + 1e-3, "and keeps within its curve's depth of the middle: " + Arrays.toString(point));
        check(Math.abs(p[last][3] - steps(p)) < 1e-2, "Slower where firm: 6 - 3 (p - .1) / .85 ms a step");
        // Any size up to the most a row holds stays inside the row; a broad one lies flatter.
        for (int height : new int[]{16, 18, 22, 40})
            for (float size = 1; size <= WildSample.trayCap(height); size += .5f) {
                float amp = WildSample.trayAmp(size, height);
                check(height / 2f - amp - size / 2 >= 0 && height / 2f + amp + size / 2 <= height && amp <= height * .1f + 1e-4,
                        "A " + size + " px tray stroke inside a " + height + " px row");
            }
        drawnEnds();
        check(WildSample.trayAmp(WildSample.trayCap(20), 20) <= WildSample.trayAmp(2, 20), "A broad stroke lies flatter");
        int black = 0;
        for (int px : WildSample.modular(ToolSlots.first(0).parts, 1, WildSample.Shape.TRAY, 60, 20, true)) if (px == 0xff000000) black++;
        check(black > 30, "A tray sample draws: " + black);
        ToolSwatches.Look big = new ToolSwatches.Look(InkWashSizes.Preset.LARGE, 120, 1), fine = new ToolSwatches.Look(InkWashSizes.Preset.SMALL, 4, 1);
        ModularBrush parts = ModularStyle.all().get(0).roll(new java.util.Random(3));
        check(WildSample.shrunk(big, 22) && !WildSample.shrunk(fine, 22), "A tile narrower than the slot draws says so");
        int[] at2 = WildSample.modular(parts, 1, WildSample.Shape.TRAY, 2, 60, 20, true), at10 = WildSample.modular(parts, 1, WildSample.Shape.TRAY, 10, 60, 20, true);
        int[] lighter = WildSample.modular(parts, .3, WildSample.Shape.TRAY, 10, 60, 20, true);
        check(marked(at10) > marked(at2) && marked(at2) > 0 && WildSample.modular(parts, 1, WildSample.Shape.TRAY, 10, 60, 20, true) == at10 && !Arrays.equals(lighter, at10),
                "A six-part slot's tray stroke at its size and grey, kept by both");
        check(WildSample.modular(parts, 1, WildSample.Shape.TRAY, 10, 60, 20, true) != WildSample.modular(parts, 1, WildSample.Shape.LOOP, 10, 60, 20, true), "kept apart from the loop");
        // A very faint stroke reads (the owner, 2026-10-19): a thin, sparse one (a light pencil in the lightest grey) is
        // drawn a little darker, its densest patch to about LEGIBLE_AT; a pale wash, broad enough to read, and a dark
        // stroke are as drawn. The Manta's tray row: 90 x 49 px.
        double lightest = ToolSwatches.GREYS[3];
        ModularBrush pencil = null, wash = null;
        for (ToolPresets.Preset q : ToolPresets.all()) { if (q.name.equals("2H pencil")) pencil = q.brush().parts; if (q.name.equals("Soft wash")) wash = q.brush().parts; }
        int[] faint = WildSample.modular(pencil, lightest, WildSample.Shape.TRAY, 8, 90, 49, true), read = WildSample.legibleTray(pencil, lightest, 8, 90, 49, true);
        check(WildSample.densest(faint, 90, 49) < WildSample.LEGIBLE && WildSample.ink(faint) < WildSample.LEGIBLE_INK && WildSample.densest(read, 90, 49) >= .3
                && WildSample.densest(read, 90, 49) <= WildSample.LEGIBLE, "A 2H pencil in Lighter reads: densest " + WildSample.densest(faint, 90, 49) + " to " + WildSample.densest(read, 90, 49));
        check(WildSample.modular(pencil, lightest, WildSample.Shape.TRAY, 8, 90, 49, true) == faint, "its sample kept as drawn");
        float washPx = (float) Math.min(ModularSizes.size(wash, InkWashSizes.Preset.MEDIUM), WildSample.trayCap(49));
        // A wash in Lighter as kept before washes reached black (ModularSizes.sinceBlack): its pale 11 % glaze.
        double paleGrey = ModularSizes.sinceBlack(wash, lightest);
        int[] pale = WildSample.modular(wash, paleGrey, WildSample.Shape.TRAY, washPx, 90, 49, true);
        check(WildSample.densest(pale, 90, 49) < WildSample.LEGIBLE && WildSample.legibleTray(wash, paleGrey, washPx, 90, 49, true) == pale, "A Lighter wash, broad enough to read, as drawn");
        int[] drawn = WildSample.modular(parts, 1, WildSample.Shape.TRAY, 10, 60, 20, true);
        check(WildSample.legibleTray(parts, 1, 10, 60, 20, true) == drawn, "A dark stroke as drawn");
    }
    /**
     * The tray's drawings (DrawLayout.PICKER, trimmed by TIP_TRIM and fitted as InkWashBar.drawing does) end, typically,
     * at DrawLayout.TIP_END of the Manta's tray row; a stroke with no drawing, in from the row's left edge firm, ends there too.
     */
    static void drawnEnds() {
        DrawLayout manta = new DrawLayout(1024, 1365);
        int w = 2 * px((manta.inner + manta.edge) / 2) - px(DrawLayout.barRoom()), h = 2 * px(manta.slot / 2) - 2 * px(DrawLayout.TRAY_TOP);
        List<Double> ends = new ArrayList<>();
        for (String[] tool : DrawLayout.PICKER) {
            if (tool[1] == null) continue;
            double[] art = inkRight(tool[1]);
            double cut = art[0] * DrawLayout.TIP_TRIM, scale = Math.min(1, Math.min(w / (art[0] - cut), h / art[1]));
            ends.add((art[2] - cut) * scale);
        }
        Collections.sort(ends);
        double end = ends.get(ends.size() / 2);
        check(Math.abs(end - DrawLayout.TIP_END * w) <= 1, "The drawings end at TIP_END of a " + w + " x " + h + " row: " + end + " px");
        for (String name : new String[]{"Mold ink", "Halftone brush"}) {
            Brush b = ToolKits.preset(name).brush();
            check(DrawLayout.drawing(b) == null, name + " has no drawing");
            double size = Math.min(ModularSizes.size(b.parts, InkWashSizes.Preset.MEDIUM), WildSample.trayCap(h));
            int[] tray = WildSample.slotTray(b.parts, new ToolSwatches.Look(InkWashSizes.Preset.MEDIUM, size, 1), w, h);
            int last = -1;
            for (int x = 0; x < w; x++) if (inked(tray, w, h, x) > 0) last = x;
            check(inked(tray, w, h, 0) * 2 >= inked(tray, w, h, w / 3), name + ": in from the row's edge, no taper in: " + inked(tray, w, h, 0) + " px tall there, " + inked(tray, w, h, w / 3) + " a third in");
            check(Math.abs(last + 1 - end) <= 2, name + ": ends where the drawings do, " + (last + 1) + " of " + end + " px");
        }
    }
    /** How many of column {@code x}'s pixels in {@code argb} ({@code w × h} greys) are inked. */
    static int inked(int[] argb, int w, int h, int x) { int n = 0; for (int y = 0; y < h; y++) if ((argb[y * w + x] & 0xff) < 255) n++; return n; }
    /** The Manta's px for {@code dp}, as Ui.dp. */
    static int px(float dp) { return Math.round(dp * 1920 / 1024f); }
    /**
     * Drawing {@code name}'s width and height in px and where its ink ends at the right: a traced vector's path (potrace's
     * M, m, l, c, z), at its points and control points, its group scaled about its pivot and moved as Android draws it; a
     * PNG's last inked column.
     */
    static double[] inkRight(String name) {
        try {
            java.io.File vector = new java.io.File("app/src/main/res/drawable/" + name + ".xml");
            if (!vector.isFile()) {
                java.awt.image.BufferedImage png = javax.imageio.ImageIO.read(new java.io.File("app/src/main/res/drawable-nodpi/" + name + ".png"));
                int right = 0;
                for (int y = 0; y < png.getHeight(); y++) for (int x = 0; x < png.getWidth(); x++) if ((png.getRGB(x, y) & 0xff) < 255) right = Math.max(right, x + 1);
                return new double[]{png.getWidth(), png.getHeight(), right};
            }
            String xml = new String(java.nio.file.Files.readAllBytes(vector.toPath()));
            String data = xml.substring(xml.indexOf("pathData=\"") + 10);
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("[MmLlCcZz]|-?[\\d.]+").matcher(data.substring(0, data.indexOf('"')));
            double x = 0, startX = 0, right = -1e9;
            char command = 'M';
            List<Double> numbers = new ArrayList<>();
            while (m.find()) {
                String token = m.group();
                if (Character.isLetter(token.charAt(0))) { command = token.charAt(0); if (command == 'z' || command == 'Z') x = startX; continue; }
                numbers.add(Double.parseDouble(token));
                int needed = command == 'c' || command == 'C' ? 6 : 2;
                if (numbers.size() < needed) continue;
                double from = Character.isLowerCase(command) ? x : 0;
                for (int i = 0; i < needed; i += 2) right = Math.max(right, numbers.get(i) + from);
                x = numbers.get(needed - 2) + from;
                if (command == 'M' || command == 'm') { startX = x; command = command == 'M' ? 'L' : 'l'; }
                numbers.clear();
            }
            double scale = attribute(xml, "scaleX", 1), pivot = attribute(xml, "pivotX", 0), width = attribute(xml, "width", 0);
            return new double[]{width, attribute(xml, "height", 0), ((right - pivot) * scale + pivot + attribute(xml, "translateX", 0)) * width / attribute(xml, "viewportWidth", 0)};
        } catch (java.io.IOException e) { throw new AssertionError(name + ": " + e); }
    }
    private static double attribute(String xml, String name, double otherwise) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("android:" + name + "=\"(-?[\\d.]+)").matcher(xml);
        return m.find() ? Double.parseDouble(m.group(1)) : otherwise;
    }
    static int marked(int[] pixels) { int n = 0; for (int px : pixels) if (px != 0xffffffff) n++; return n; }
    static double steps(float[][] p) { double sum = 0; for (int i = 1; i < p.length; i++) sum += 6 - 3 * (p[i][2] - .1) / .85; return sum; }
    static void near(float[] point, float x, float y, float pressure, float time, String which) {
        check(Math.abs(point[0] - x) < 1e-3 && Math.abs(point[1] - y) < 1e-3 && Math.abs(point[2] - pressure) < 1e-4 && Math.abs(point[3] - time) < 1e-2,
                "The " + which + " point is the mockup's: " + Arrays.toString(point));
    }
    static void sample() {
        int side = 72;
        ModularBrush brush = ToolSlots.first(0).parts;
        int[] sample = WildSample.modular(brush, 1, WildSample.Shape.LOOP, side, side, true);
        check(sample.length == side * side, "A sample is the tile");
        int black = 0, edge = 0;
        for (int y = 0; y < side; y++) for (int x = 0; x < side; x++) {
            boolean ink = sample[y * side + x] == 0xff000000;
            if (ink) black++;
            if (ink && (x < 3 || x >= side - 3 || y < 3 || y >= side - 3)) edge++;
        }
        check(black > side * 2 && edge == 0, "The loop crosses the tile, clear of its sides: " + black + " black, " + edge + " at the sides");
        check(sample[0] == 0xffffffff && sample[side * side - 1] == 0xffffffff, "Paper around it is white");
        int lighter = 0;
        for (int p : WildSample.modular(brush, .53, WildSample.Shape.LOOP, side, side, true)) if (p == 0xff000000) lighter++;
        check(lighter < black, "A lighter grey shows lighter");
        check(WildSample.modular(brush, 1, WildSample.Shape.LOOP, 200, 140, true).length == 200 * 140, "A card's sample is the card");
        for (int slot = 3; slot < ToolSlots.COUNT; slot++) {
            int[] paint = WildSample.modular(ToolSlots.first(slot).parts, 1, WildSample.Shape.LOOP, 68, 68, true);
            int painted = 0;
            for (int p : paint) if (p != 0xffffffff) painted++;
            check(painted > (slot == 4 ? 20 : 68 * 68 / 25), "A paint sample shows its paint, " + ToolSlots.first(slot).name() + ": " + painted);
        }
    }
    static void kept() {
        int side = 72;
        ModularBrush brush = ModularStyle.named("Brush pen").roll(new java.util.Random(505));
        int[] sample = WildSample.modular(brush, 1, WildSample.Shape.LOOP, side, side, true);
        check(WildSample.modular(brush.copy(), 1, WildSample.Shape.LOOP, side, side, true) == sample, "The same settings are the sample kept");
        ModularBrush renamed = brush.copy(); renamed.name = "another";
        check(WildSample.modular(renamed, 1, WildSample.Shape.LOOP, side, side, true) == sample, "A name doesn't change a sample");
        check(WildSample.modular(brush.copy(brush.seed + 1), 1, WildSample.Shape.LOOP, side, side, true) != sample
                && WildSample.modular(brush, .5, WildSample.Shape.LOOP, side, side, true) != sample, "Other settings or another darkness, another sample");
        for (int seed = 1; seed <= WildSample.KEPT; seed++) WildSample.modular(brush.copy(seed), 1, WildSample.Shape.LOOP, side, side, true);
        int[] again = WildSample.modular(brush, 1, WildSample.Shape.LOOP, side, side, true);
        check(again != sample && Arrays.equals(again, sample), "Past " + WildSample.KEPT + " the oldest is dropped, and drawn again the same");
        int[] once = WildSample.modular(brush, 1, WildSample.Shape.LOOP, 90, 60, false);
        check(Arrays.equals(once, WildSample.modular(brush, 1, WildSample.Shape.LOOP, 90, 60, false)) && WildSample.modular(brush, 1, WildSample.Shape.LOOP, 90, 60, false) != once,
                "A sample drawn once (the lab's corner) is the same drawing, not kept");
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    /**
     * The one grey (2026-10-24): a slot's picture (the bar's row, the brush line, the Tools page's and Brush Lab's slots)
     * is the brush at full darkness, the same whatever grey is in use; a brush that lays white is a light stroke on a
     * dark ground, every other brush on paper.
     */
    static void neutral() {
        int w = 60, h = 20;
        java.util.List<String> dark = new java.util.ArrayList<>();
        for (ToolPresets.Preset p : ToolPresets.all()) {
            ModularBrush b = p.brush().parts;
            double px = Math.min(ModularSizes.size(b, InkWashSizes.Preset.MEDIUM), WildSample.trayCap(h));
            int[] black = WildSample.slotTray(b, new ToolSwatches.Look(InkWashSizes.Preset.MEDIUM, px, 1), w, h);
            check(Arrays.equals(black, WildSample.slotTray(b, new ToolSwatches.Look(InkWashSizes.Preset.MEDIUM, px, .2), w, h))
                    && Arrays.equals(black, WildSample.legibleTray(b, WildSample.PREVIEW, (float) px, w, h, true)), p.name + ": its picture the same at any grey in use, at full darkness");
            // The tile's corner, clear of the stroke: the ground.
            long ink = 0;
            for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) ink += 255 - (black[y * w + x] & 0xff);
            boolean white = WildSample.darkGround(b);
            check(white == (ResolvedBrush.of(b, 8, 1).blendMode == ResolvedBrush.BlendMode.WHITE), p.name + ": a dark ground only for a brush that lays white");
            if (white) check(ink > 16 * 255 / 2, p.name + ": lays white, on a dark ground (corner ink " + ink + ")");
            if (java.util.Arrays.asList("Fineliner", "Soft wash", "Wet gouache", "Sketch pencil", "Steady line").contains(p.name)) check(ink == 0, p.name + ": on paper");
            if (white) {
                int light = 0;
                for (int i = w * h / 3; i < 2 * w * h / 3; i++) if ((black[i] & 0xff) == 255) light++;
                check(light > w * h / 12, p.name + ": its white stroke shows on the ground: " + light + " px");
                dark.add(p.name);
            }
        }
        check(dark.contains("White gel pen"), "The White gel pen is on a dark ground: " + dark);
        System.out.println("  On a dark ground: " + dark);
    }
}
