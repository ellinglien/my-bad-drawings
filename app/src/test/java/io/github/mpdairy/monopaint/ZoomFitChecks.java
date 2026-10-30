package io.github.mpdairy.monopaint;

/**
 * The fitted page (the owner, 2026-10-26: "when switching to zoom mode or back.. will some part of the image be obscured?
 * let's make it fit neatly in both"). On the Manta and the Nomad, either way up, the pad is the room the column, the page
 * bar and the brush line leave (PaintActivity.showInkWashBar's layout, in px). Every page, whatever screen it was made
 * on, shows whole, centred, at the fitted scale (but an upright page on a screen turned sideways fills the width, its
 * top showing: the owner, 2026-10-28); Zen Mode's whole screen fits it again; Zoom's double tap (a reset) comes
 * back to exactly that view; a pinch still stops at 100%. Locking Zoom keeps the view (PaintActivity, not here).
 */
final class ZoomFitChecks {
    static final float DENSITY = 1.875f;  // the Manta's and the Nomad's 300 dpi
    static int px(int dp) { return Math.round(dp * DENSITY); }

    public static void main(String[] args) {
        for (int[] s : DrawLayoutChecks.SCREENS) {
            int[] zen = panel(s), pad = pad(s), turned = pad(new int[]{s[1], s[0]});
            // A page made here, one made the other way up (shown turned: as wide as that pad was tall), one made in Zen Mode.
            for (int[] page : new int[][]{pad, {turned[1], turned[0]}, zen}) {
                CanvasViewport v = new CanvasViewport();
                v.reset(); v.configure(pad[0], pad[1], page[0], page[1]);
                fitted(v, pad, page, s);
                float x = v.x, y = v.y, scale = v.scale();
                v.gesture(.01f, 0, 0, 0, 0);
                check(v.scale() == scale, "a pinch out of the fit stays at the fit");
                v.gesture(3, pad[0] / 2f, pad[1] / 2f, pad[0] / 3f, pad[1] / 3f);
                check(v.scale() > scale && !v.untouched(), "a pinch zooms in");
                v.gesture(.01f, 0, 0, 0, 0);
                boolean sideways = pad[0] > pad[1] && page[1] > page[0];
                check(Math.abs(v.scale() - (sideways ? scale : 1)) < 1e-4, sideways ? "sideways, a pinch out stops at the width filled" : "a pinch out still stops at 100%");
                v.reset(); v.configure(pad[0], pad[1], page[0], page[1]);
                check(v.x == x && v.y == y && v.scale() == scale, "the double tap comes back to exactly the fit");
                // Zen Mode while fitted: the page fitted again in the whole screen; and back out.
                v.configure(zen[0], zen[1], page[0], page[1]); fitted(v, zen, page, s);
                v.configure(pad[0], pad[1], page[0], page[1]);
                check(v.x == x && v.y == y && v.scale() == scale, "out of Zen Mode, the same fit as before");
            }
        }
        int[] manta = pad(DrawLayoutChecks.SCREENS[0]);
        // 2401 px tall until layout v3 (2026-10-26), whose brush line grew from 18 dp to 32 with its preview.
        check(manta[0] == 1785 && manta[1] == 2387, "the Manta's pad: " + manta[0] + " x " + manta[1]);
        backdrop(manta);
        System.out.println("PASS: the page whole and centred in the room the bars leave (the Manta's 1785 x 2387 px), Manta and Nomad either way up, pages from either turn and from Zen Mode; the double tap and leaving Zen Mode come back to exactly that fit; a pinch still never below 100%; the lines beyond the page at every zoom, never in a copy");
    }
    /**
     * The backdrop (the owner, 2026-10-27: "a background for when the canvas is < 100% and you can see it ... (lines)"):
     * the room around the page shows the owner's lines at every zoom (a pinch or pan to 100% still leaves room), and only
     * the drawing screen's raster asks for them, so no export, PNG, PDF or page picture has them.
     */
    static void backdrop(int[] pad) {
        try {
            java.io.File dir = new java.io.File("app/src/main/java/io/github/mpdairy/monopaint");
            for (java.io.File f : dir.listFiles()) {
                String src = new String(java.nio.file.Files.readAllBytes(f.toPath()));
                if (!src.contains(".backdrop =") && !src.contains(".backdrop=")) continue;
                check(f.getName().equals("DrawingPad.java") && src.split("\\.backdrop =", -1).length == 2
                        && src.contains("viewportBitmap.backdrop = true;"), "only the drawing screen's raster asks for the lines: " + f.getName());
            }
            String raster = new String(java.nio.file.Files.readAllBytes(new java.io.File(dir, "ViewportBitmap.java").toPath()));
            String dithered = raster.substring(raster.indexOf("static Bitmap dithered("), raster.indexOf("final Bitmap bitmap;"));
            check(raster.contains("    boolean backdrop;") && !dithered.contains("backdrop"), "a raster starts without the lines; a copy's (dithered) never asks");
            String c = new String(java.nio.file.Files.readAllBytes(new java.io.File("app/src/main/cpp/viewport_bitmap.c").toPath()));
            check(c.contains("return lines && ") && c.split("backdrop\\(lines,x,y\\)", -1).length == 3, "the native raster draws lines beyond the page only when asked, paper otherwise");
        } catch (java.io.IOException e) { throw new AssertionError(e); }
    }
    /** The panel, px, of a {@code width x height} dp screen: the Manta's 1920 x 2560 or the Nomad's 1404 x 1872, that way up. */
    static int[] panel(int[] s) {
        int[] p = Math.min(s[0], s[1]) >= DrawLayout.WIDE ? new int[]{1920, 2560} : new int[]{1404, 1872};
        return s[0] < s[1] ? p : new int[]{p[1], p[0]};
    }
    /** The pad's size, px: the panel less the column, the page bar and the foot row. */
    static int[] pad(int[] s) {
        DrawLayout l = new DrawLayout(s[0], s[1]);
        int[] p = panel(s);
        return new int[]{p[0] - px(l.side), p[1] - px(l.pageBar()) - px(l.footHeight())};
    }
    private static void fitted(CanvasViewport v, int[] view, int[] page, int[] s) {
        String at = s[0] + "x" + s[1] + " dp, page " + page[0] + "x" + page[1] + " in " + view[0] + "x" + view[1];
        float w = page[0] * v.scale(), h = page[1] * v.scale();
        if (view[0] > view[1] && page[1] > page[0]) {
            // Turned sideways, an upright page: the width filled (past 100% if need be), the top showing, the rest a pan away.
            check(v.scale() == view[0] / (float) page[0] && v.y == 0 && v.x == 0, "the width filled, the top showing, " + at);
            return;
        }
        check(v.scale() == Math.min(1, Math.min(view[0] / (float) page[0], view[1] / (float) page[1])), "the fitted scale, " + at);
        check(v.x >= 0 && v.y >= 0 && v.x + w <= view[0] + .5f && v.y + h <= view[1] + .5f, "nothing hidden, " + at);
        check(Math.abs(v.x - (view[0] - w - v.x)) <= 1 && Math.abs(v.y - (view[1] - h - v.y)) <= 1, "centred, " + at);
        check(v.x == Math.round(v.x) && v.y == Math.round(v.y), "on whole pixels, " + at);
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
