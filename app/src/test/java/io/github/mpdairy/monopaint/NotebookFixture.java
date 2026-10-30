package io.github.mpdairy.monopaint;

import java.util.Random;

/** Pages drawn with the app's own brushes, for the notebook checks and their timings: a sketch of ink lines over washes. */
final class NotebookFixture {
    private static boolean installed;

    /** A {@code width × height} page, its wash layer washed with a grainy watercolour, its ink layer lined with a fineliner. */
    static ToneDocument sketch(int width, int height, long seed) {
        if (!installed) { Assets.install(Assets.folder(Assets.FOLDER)); installed = true; }
        Random random = new Random(seed);
        ToneDocument page = InkWashLayers.blank(width, height);
        WatercolorSession session = new WatercolorSession();
        ModularBrush wash = brush("Watercolor", "Granulating"), line = brush("Fineliner", "Steady line");
        page.selectLayer(InkWashLayers.WASH);
        for (int i = 0; i < 4; i++) stroke(page, wash, Math.max(8, width / 16.0), .4 + .4 * random.nextDouble(), session, random);
        session.settleNow(page);
        page.selectLayer(InkWashLayers.INK);
        for (int i = 0; i < 14; i++) stroke(page, line, Math.max(1.5, width / 640.0), 1, session, random);
        session.settleNow(page);
        return page;
    }

    static ModularBrush brush(String style, String name) {
        for (String[] row : ToolPresets.ROWS) if (row[0].equals(style) && row[1].equals(name)) return ModularCode.decode(row[2]);
        throw new AssertionError("No preset " + style + " " + name);
    }

    /** One wavy stroke across part of the page. */
    static void stroke(ToneDocument page, ModularBrush brush, double size, double darkness, WatercolorSession session, Random random) {
        ModularStroke s = new ModularStroke(page, brush, size, darkness, session);
        double x0 = page.width * (.1 + .3 * random.nextDouble()), y0 = page.height * (.1 + .8 * random.nextDouble());
        double length = page.width * (.3 + .5 * random.nextDouble()), wave = page.height * .05 * random.nextDouble();
        int n = 120;
        long time = 0;
        for (int i = 0; i <= n; i++) {
            double u = i / (double) n, env = Math.sin(Math.PI * u);
            time += 4;
            s.sample((float) (x0 + length * u), (float) (y0 + wave * Math.sin(3 * Math.PI * u)),
                    ToolSettings.pressure((float) (.2 + .7 * env)), 20, 30, time);
            if (i % 3 == 0) s.present();
        }
        s.finish();
    }

    private NotebookFixture() {}
}
