package io.github.mpdairy.monopaint;

import java.util.Random;

public final class PartSampleChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        wave(); ramp(); lift(); everyStyle(); waves(); band(); kept();
        System.out.println("PASS: part cards and swap tiles draw a wave of what their part adds (the tip's wave, for medium over a grey band; a wave that lifts firm over the band for drying; bands for carry; a flatter wave for paper; a pressure ramp with a turning tilt for the hand), on both tablets' tiles, for every style, kept per role");
    }
    static void wave() {
        float[][] p = WildSample.wave(400, 120, 20, 30, 1.25, WildSample.Press.SWELL, false);
        check(p.length >= 30, "At least 30 points: " + p.length);
        check(Math.abs(p[0][0] - 20) < 1e-3 && Math.abs(p[p.length - 1][0] - 380) < 1e-3, "From the pad to the pad");
        float middle = p[p.length / 2][2];
        check(middle > p[0][2] && middle > p[p.length - 1][2], "Firm in the middle, light at the ends");
        for (int i = 0; i < p.length; i++) {
            check(p[i][4] == 0 && p[i][5] == 0, "Upright");
            if (i > 0) check(p[i][3] > p[i - 1][3], "Time runs on");
        }
    }
    static void ramp() {
        float[][] p = WildSample.wave(400, 120, 20, 30, 1, WildSample.Press.RAMP, true);
        for (int i = 1; i < p.length; i++) check(p[i][2] >= p[i - 1][2], "Pressure never falls");
        float[] last = p[p.length - 1];
        check(Math.abs(BrushDirection.inclination(last[4], last[5]) - 55) < 1, "Leaning about 55 degrees at the end: " + BrushDirection.inclination(last[4], last[5]));
        check(BrushDirection.inclination(p[0][4], p[0][5]) == 0, "Upright at the start");
    }
    /** Drying's wave: light at the start, firm from the loop's firm point to the lift, slowing as it firms. */
    static void lift() {
        float[][] p = WildSample.wave(400, 120, 20, 30, 1, WildSample.Press.LIFT, false);
        float last = p[p.length - 1][2];
        check(p[0][2] < .2 && last > .85, "Light at the start, firm at the lift: " + p[0][2] + ", " + last);
        for (int i = 1; i < p.length; i++) {
            check(p[i][2] >= p[i - 1][2] - 1e-6, "Pressure never falls");
            if (p[i][0] >= 20 + 360 * WildSample.LOOP_FIRM + 1) check(Math.abs(p[i][2] - last) < 1e-6, "Firm from the loop's firm point on");
        }
        check(p[p.length - 1][3] - p[p.length - 2][3] > p[1][3] - p[0][3], "Slower where firm");
    }
    /**
     * Every part's sample is a wave, not a whole brush's loop (DESIGN.md "Loop and wave"): at each tablet's module
     * sample and swap tiles, its mark well wider than tall and across most of the tile (a loop stands, a little taller than wide, in the middle). Float's rings are its own sample.
     */
    static void waves() {
        ModularBrush[] brushes = new ModularBrush[3];
        String[] names = {"Steady line", "Soft wash", "Messy stick"};
        for (int i = 0; i < names.length; i++) for (ToolPresets.Preset p : ToolPresets.all()) if (p.name.equals(names[i])) brushes[i] = p.brush().parts;
        for (int[] screen : new int[][]{LabLayoutChecks.MANTA, LabLayoutChecks.NOMAD}) {
            LabLayout l = new LabLayout(screen[0], screen[1]);
            int[] cell = l.swapCell(5);
            // A swap tile's sample: the cell but its rule, over a caption of one line, or of as many as leave it its least share.
            int[][] tiles = {{px(l.sampleWidth), px(l.sampleHeight)}, {px(cell[0] - 2), px(cell[1] - 2 - LabText.CAPTION_CHROME - LabText.CAPTION_SP * 1.4)},
                    {px(cell[0] - 2), px((cell[1] - 2) * LabText.SAMPLE_SHARE)}};
            for (ModularBrush b : brushes) for (BrushPart role : BrushPart.values()) for (int[] t : tiles) {
                int w = t[0], h = t[1];
                int[] px = WildSample.part(b, role, w, h);
                int x0 = w, x1 = -1, y0 = h, y1 = -1;
                for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                    if (inBand(role, x, w) || (px[y * w + x] & 0xff) >= 238) continue;
                    x0 = Math.min(x0, x); x1 = Math.max(x1, x); y0 = Math.min(y0, y); y1 = Math.max(y1, y);
                }
                check(x1 - x0 > 1.3 * (y1 - y0) && x1 - x0 > w * .6, "A " + role + " sample is a wave across its " + w + " x " + h + " tile (" + screen[0]
                        + "), its mark " + (x1 - x0) + " x " + (y1 - y0));
            }
        }
    }
    /** Both tablets' px a dp. */
    static int px(double dp) { return (int)Math.round(dp * 1.875); }
    static void everyStyle() {
        Random random = new Random(77);
        int w = 200, h = 120;
        for (ModularStyle style : ModularStyle.all()) {
            ModularBrush b = style.roll(random);
            for (BrushPart role : BrushPart.values()) {
                int[] px = WildSample.part(b, role, w, h);
                check(px.length == w * h, "A part sample is the tile");
                int dark = 0;
                for (int y = 0; y < h; y++) for (int x = 0; x < w; x++)
                    if (!inBand(role, x, w) && (px[y * w + x] & 0xff) < 238) dark++;
                check(dark > 100, "A part sample shows its mark: " + style.name + " " + role + " " + dark);
            }
        }
    }
    /** In the band behind a sample, or for Carry its darker strip. */
    static boolean inBand(BrushPart role, int x, int w) {
        if (role == BrushPart.CARRY && x >= Math.round(w * .2f) && x < Math.round(w * .24f)) return true;
        return (role == BrushPart.MEDIUM || role == BrushPart.CARRY || role == BrushPart.LIFE) && x >= Math.round(w * .42f) && x < Math.round(w * .58f);
    }
    static void band() {
        ModularBrush b = ModularStyle.all().get(0).roll(new Random(77));
        int[] px = WildSample.part(b, BrushPart.MEDIUM, 200, 120);
        // The page's dot screen: grey is a mix of black and white dots, read over one 8 x 8 cell.
        int black = 0;
        for (int y = 0; y < 8; y++) for (int x = 96; x < 104; x++) if (px[y * 200 + x] == 0xff000000) black++;
        check(black > 4 && black < 60, "The band at (100, 2), off the stroke, is grey: " + black + " of 64 dots black");
        int white = 0;
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) if (px[y * 200 + x] == 0xffffffff) white++;
        check(white == 64, "Paper beside it is white");
    }
    static void kept() {
        ModularBrush b = ModularStyle.all().get(3).roll(new Random(5));
        int[] tip = WildSample.part(b, BrushPart.TIP, 200, 120);
        check(WildSample.part(b, BrushPart.TIP, 200, 120) == tip, "The same call is the sample kept");
        check(WildSample.part(b, BrushPart.PAPER, 200, 120) != tip, "Another role, another sample");
    }
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
