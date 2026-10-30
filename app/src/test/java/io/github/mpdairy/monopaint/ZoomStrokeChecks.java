package io.github.mpdairy.monopaint;

/**
 * A stroke drawn zoomed in lays the mark it lays at 100% ({@link DrawingStroke#zoom}). At 800% the same hand on the
 * glass draws the same page path with eight times the samples (the pen reports 480 a second whatever the zoom) at an
 * eighth of the page speed; sizes are page pixels, and the pen's speed is read on the glass, as the brushes' Control
 * and the eraser's {@link PenSpeed} are calibrated. Read on the page, every brush that thins with speed went fat.
 */
public final class ZoomStrokeChecks {
    static final int W = QuickStrokeChecks.W, H = QuickStrokeChecks.H, N = 420;

    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        sameMark(); eraser(); unzoomed();
        System.out.println("PASS: zoomed in to 200% and 800%, a brush lays the mark it lays at 100% (its ink within 6%, a speed-thinned Brush pen and Anything goes too; read on the page they went 13 to 78% heavier), quick and live alike; the eraser grows with the hand's speed as at 100%; at 100% every stroke is as before, byte for byte");
    }

    /** The checks' S across the page, {@code n} samples 2 ms apart (480 Hz): at 800% the hand covers the same page path in 8 times the samples. */
    static float[][] path(int n) {
        float[][] s = new float[n][];
        for (int i = 0; i < n; i++) {
            double u = (double) i / (n - 1);
            s[i] = new float[]{(float) (120 + 660 * u), (float) (350 + 200 * Math.sin(2 * Math.PI * u)), (float) (.25 + .6 * Math.sin(Math.PI * u)),
                    (float) (20 * Math.cos(3 * u)), 30, 10_000 + 2 * i};
        }
        return s;
    }
    static ToneDocument draw(ModularBrush b, double size, int zoom, float read) {
        ToneDocument d = InkWashLayers.blank(W, H);
        DrawingStroke s = QuickStroke.start(d, b, size, 1, new WatercolorSession());
        s.zoom(read);
        QuickStrokeChecks.draw(s, path(N * zoom), false, null);
        s.finish();
        return d;
    }
    /** The ink {@code b} lays over {@code a}'s: 1 for as much. */
    static double ink(ToneDocument a, ToneDocument b) {
        long x = 0, y = 0;
        for (int j = 0; j < H; j++) for (int i = 0; i < W; i++) { x += 255 - a.compositeTone(i, j); y += 255 - b.compositeTone(i, j); }
        return y / (double) Math.max(1, x);
    }
    static void sameMark() {
        // The styles whose width or darkness follows speed (Brush pen, Dip pen, Marker, Anything goes), and two that don't.
        for (String style : new String[]{"Brush pen", "Dip pen", "Marker", "Anything goes", "Fineliner", "Watercolor"})
            for (double size : new double[]{8, 40}) {
                ModularBrush b = QuickStrokeChecks.brush(style, PartChoice.NONE);
                String at = style + " at " + (int) size + " px (" + (QuickStroke.quick(ResolvedBrush.of(b, size, 1)) ? "quick" : "live") + ")";
                ToneDocument fitted = draw(b, size, 1, 1);
                for (int zoom : new int[]{2, 8}) {
                    double glass = ink(fitted, draw(b, size, zoom, zoom));
                    check(Math.abs(glass - 1) <= .06, at + " zoomed to " + zoom * 100 + "% lays the same ink: " + glass);
                }
                if (style.equals("Brush pen") || style.equals("Anything goes")) {
                    double page = ink(fitted, draw(b, size, 8, 1));
                    check(page > 1.12, at + ": read on the page, 800% would go heavier (" + page + "), what the zoom fixes");
                }
            }
    }
    static void eraser() {
        // The hand at 1.5 px/ms on the glass: at 100% that is 1.5 page px/ms; at 800%, 0.1875.
        for (float zoom : new float[]{1, 2, 8}) {
            PenSpeed s = new PenSpeed(); s.zoom = zoom;
            for (int i = 0; i <= 200; i++) s.sample(100 + 1.5f / zoom * 2 * i, 100, 10_000 + 2 * i);
            PenSpeed fitted = new PenSpeed();
            for (int i = 0; i <= 200; i++) fitted.sample(100 + 1.5f * 2 * i, 100, 10_000 + 2 * i);
            check(Math.abs(s.factor(PenSpeed.Effect.PEN_ERASER) - fitted.factor(PenSpeed.Effect.PEN_ERASER)) < 1e-4
                    && Math.abs(s.speed() - fitted.speed()) < 1e-3, "At " + zoom * 100 + "% the eraser grows with the hand's speed as at 100%: " + s.speed() + " px/ms");
        }
    }
    static void unzoomed() {
        for (String style : new String[]{"Brush pen", "Anything goes"}) {
            ModularBrush b = QuickStrokeChecks.brush(style, PartChoice.NONE);
            ToneDocument d = InkWashLayers.blank(W, H), e = InkWashLayers.blank(W, H);
            DrawingStroke s = QuickStroke.start(d, b, 16, 1, new WatercolorSession()), t = QuickStroke.start(e, b, 16, 1, new WatercolorSession());
            t.zoom(1);
            QuickStrokeChecks.draw(s, path(N), false, null); QuickStrokeChecks.draw(t, path(N), false, null);
            s.finish(); t.finish();
            check(ModularStrokeChecks.same(QuickStrokeChecks.layers(d), QuickStrokeChecks.layers(e)), style + ": at 100% the stroke is as before, byte for byte");
        }
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
