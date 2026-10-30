package io.github.mpdairy.monopaint;

/**
 * Zoom and its lock, brought back from MonoPaint (its TODO.md "Zoom and pan lock"): where the page lies on the canvas at any
 * zoom ({@link PageView}, {@link CanvasViewport}), and two fingers told apart as a tap (Undo, Redo: {@link
 * MultiFingerTap}) or a pinch and pan ({@link PinchPan}), with one finger the finger tool's ({@link FingerTool}). The
 * touches go through a model of {@code DrawingPad}'s order: the taps first, then the finger tool, then navigation.
 */
public final class PinchPanChecks {
    public static void main(String[] args) {
        mapping(); rotateTurnsThePage(); pinchKeepsThePage(); slops(); taps(); pinches(); threeFingers(); lock(); fingerTool(); words();
        System.out.println("PASS: the page maps to the canvas and back at 100%, 200% and 800%, panned off-centre, in all four turns, as MonoPaint's rotate, scale and move did, a changed area to the canvas pixels it covers; Rotate turns the page with the app, upright, whole and as large as fits, and back from the canvas to the page, the pen's tilt turned as gravity is; a pinch keeps the page under the fingers; two fingers stay a tap (Undo, three Redo) until one moves past the tap's own slop, then pinch and pan from where they landed and no tap fires; a third finger before they move is a three-finger tap; fingers landing close together never navigate; locked (the default), nothing moves and taps still undo; a finger's stroke is taken back when the second lands, which may then pinch; the Zoom button's words");
    }

    // ---- where the page lies ----

    /** MonoPaint's own map (DrawingPad.place before PageView), worked out by hand: rotate a quarter back, move into view, scale, move. */
    static float[] monoPaint(int quarter, int w, int h, float s, float x, float y, float px, float py) {
        float rx, ry;
        switch (quarter) {
            case 1: rx = py; ry = w - px; break;
            case 2: rx = w - px; ry = h - py; break;
            case 3: rx = h - py; ry = px; break;
            default: rx = px; ry = py;
        }
        return new float[]{rx * s + x, ry * s + y};
    }
    static void mapping() {
        int w = 952, h = 1365;
        float[][] views = {{1, 0, 0}, {2, -300, -700}, {8, -3100, -5200}, {8, -7000, -10000}, {2.5f, -10, -2000}};
        for (int q = 0; q < 4; q++)
            for (float[] v : views) {
                float[] m = PageView.map(q, w, h, v[0], v[1], v[2]), back = PageView.invert(m);
                for (float[] p : new float[][]{{0, 0}, {w, h}, {123.5f, 987.25f}, {w - 1, 3}}) {
                    float[] at = PageView.apply(m, p.clone()), want = monoPaint(q, w, h, v[0], v[1], v[2], p[0], p[1]);
                    check(near(at[0], want[0], .01f) && near(at[1], want[1], .01f), "Turn " + q + " at " + v[0] * 100 + "%: page " + p[0] + "," + p[1] + " lands where MonoPaint put it");
                    float[] again = PageView.apply(back, at.clone());
                    check(near(again[0], p[0], .01f) && near(again[1], p[1], .01f), "and the canvas point maps back to it (the pen's page point)");
                }
                // A changed area: its corners' bounds on the canvas, and every canvas pixel in them comes from inside it (ViewportBitmap.update).
                float[] corners = PageView.apply(m, new float[]{100, 200, 140, 200, 100, 260, 140, 260});
                float left = Math.min(Math.min(corners[0], corners[2]), Math.min(corners[4], corners[6])), right = Math.max(Math.max(corners[0], corners[2]), Math.max(corners[4], corners[6]));
                float top = Math.min(Math.min(corners[1], corners[3]), Math.min(corners[5], corners[7])), bottom = Math.max(Math.max(corners[1], corners[3]), Math.max(corners[5], corners[7]));
                boolean swapped = (q & 1) == 1;
                check(near(right - left, (swapped ? 60 : 40) * v[0], .01f) && near(bottom - top, (swapped ? 40 : 60) * v[0], .01f),
                        "A 40 x 60 area covers " + v[0] + " times its size on the canvas, turned " + q);
                float[] centre = PageView.apply(back, new float[]{(left + right) / 2, (top + bottom) / 2});
                check(near(centre[0], 120, .01f) && near(centre[1], 230, .01f), "its middle on the canvas is its middle on the page");
            }
        check(java.util.Arrays.equals(PageView.map(0, w, h, 1, 0, 0), new float[]{1, 0, 0, 0, 1, 0, 0, 0, 1}),
                "Upright at 100%, unmoved: exactly the identity, so the page draws unscaled, as fast as before");
    }
    /**
     * Rotate (the owner, 2026-10-27: "on rotate, rotate the canvas"): the page turns with the app, as DrawingPad lays it
     * (PageView.map with no turn of its own, CanvasViewport fitted to the turned canvas): upright, whole, centred, as large
     * as fits; a canvas point maps back to its page point at the fit and zoomed in; the panel's tilt axes turn as gravity's.
     */
    static void rotateTurnsThePage() {
        int w = 952, h = 1365, bar = 48;
        for (int q = 0; q < 4; q++) {
            boolean landscape = (q & 1) == 1;
            int vw = landscape ? h : w, vh = (landscape ? w : h) - bar;
            CanvasViewport v = new CanvasViewport();
            v.configure(vw, vh, w, h);
            float[] m = PageView.map(0, w, h, v.scale(), v.x, v.y);
            check(m[1] == 0 && m[3] == 0 && m[0] > 0 && m[4] > 0, "Turn " + q + ": the page lies upright in the turned canvas");
            float[] c = PageView.apply(m, new float[]{0, 0, w, h});
            // Upright: the whole page, centred, as large as fits. Sideways (the owner, 2026-10-28): the width filled, the top showing.
            if (landscape) check(near(c[0], 0, 1) && near(c[2], vw, 1) && near(c[1], 0, .5f) && c[3] > vh && v.percent() == Math.round(100f * vw / w),
                    "A portrait page on a landscape screen fills its width, its top showing: " + v.percent() + "%");
            else check(c[0] >= -.5f && c[1] >= -.5f && c[2] <= vw + .5f && c[3] <= vh + .5f && (near(c[2] - c[0], vw, 1) || near(c[3] - c[1], vh, 1))
                    && near(c[0], vw - c[2], 1) && near(c[1], vh - c[3], 1), "the whole page shows, as large as fits, centred, turn " + q);
            for (int zoomed = 0; zoomed < 2; zoomed++) {
                if (zoomed == 1) { v.gesture(3, vw / 3f, vh / 3f, vw / 3f, vh / 3f); m = PageView.map(0, w, h, v.scale(), v.x, v.y); }
                float[] back = PageView.invert(m);
                for (float[] p : new float[][]{{0, 0}, {w, h}, {123.5f, 987.25f}, {w - 1, 3}}) {
                    float[] again = PageView.apply(back, PageView.apply(m, p.clone()));
                    check(near(again[0], p[0], .01f) && near(again[1], p[1], .01f), "Turn " + q + " at " + v.percent() + "%: page to canvas and back lands on " + p[0] + "," + p[1]);
                }
            }
            float[] tilt = PageView.turn(q, 25, -55), undone = PageView.turn((4 - q) % 4, tilt[0], tilt[1]);
            check(near(undone[0], 25, .001f) && near(undone[1], -55, .001f), "A turn and its opposite give the pen's tilt back, turn " + q);
            double[] pull = PageTilt.fromDevice(3, -7, q), upright = PageTilt.fromDevice(3, -7, 0);
            float[] turned = PageView.turn(q, (float)upright[0], (float)upright[1]);
            check(near(turned[0], (float)pull[0], .0001f) && near(turned[1], (float)pull[1], .0001f), "The pen's tilt turns as gravity does, turn " + q);
        }
    }
    /** A pinch through CanvasViewport, the page mapped by PageView: the page point under the fingers stays under them. */
    static void pinchKeepsThePage() {
        int w = 952, h = 1365;
        for (int q = 0; q < 4; q++) {
            boolean swapped = (q & 1) == 1;
            int vw = swapped ? h : w, vh = swapped ? w : h;
            CanvasViewport v = new CanvasViewport();
            v.configure(vw, vh, swapped ? h : w, swapped ? w : h);
            PinchPan p = new PinchPan();
            float ax = 300, ay = 400, bx = 360, by = 480;
            float[] under = PageView.apply(PageView.invert(PageView.map(q, w, h, v.scale(), v.x, v.y)), new float[]{(ax + bx) / 2, (ay + by) / 2});
            check(p.land(false, 1, ax, ay, 2, bx, by, 24), "Two fingers land, turn " + q);
            for (int step = 1; step <= 40; step++) {
                float spread = 4 * step, drift = 3 * step;
                float nax = ax - spread + drift, nay = ay - spread, nbx = bx + spread + drift, nby = by + spread;
                p.starts(nax, nay, nbx, nby, 18);
                float[] g = p.step(nax, nay, nbx, nby, 24);
                if (g != null) v.gesture(g[0], g[1], g[2], g[3], g[4]);
                if (v.scale() >= 8) break;
                float[] now = PageView.apply(PageView.map(q, w, h, v.scale(), v.x, v.y), under.clone());
                if (v.x < 0 && v.x > vw - vw * v.scale() && v.y < 0 && v.y > vh - vh * v.scale())
                    check(near(now[0], (nax + nbx) / 2, .5f) && near(now[1], (nay + nby) / 2, .5f),
                            "At " + v.percent() + "% the page point first under the fingers is still under them, turn " + q);
            }
            check(v.percent() > 200 && v.percent() <= 800, "A wide pinch zooms in, at most 800%: " + v.percent() + "%");
            v.gesture(.01f, vw / 2f, vh / 2f, vw / 2f, vh / 2f);
            check(v.percent() == 100 && v.x == 0 && v.y == 0, "Pinched inward, the page is fitted again");
            v.gesture(4, 100, 100, 100, 100); v.reset(); v.configure(vw, vh, swapped ? h : w, swapped ? w : h);
            check(v.percent() == 100 && v.x == 0 && v.y == 0, "A new page or a turn of the tablet starts fitted (CanvasViewport.reset)");
        }
    }

    // ---- two fingers ----

    /** A touch on the canvas: action (Android's: 0 down, 1 up, 2 move, 3 cancel, 5 pointer down, 6 pointer up), its time, and its fingers {id, x, y}. */
    static final class Touch {
        final int action; final long time; final float[][] fingers;
        Touch(int action, long time, float[]... fingers) { this.action = action; this.time = time; this.fingers = fingers; }
    }
    /** DrawingPad's order for fingers alone (no pen near): historyTap, then fingerStroke, then navigationGesture. */
    static final class Pad {
        final MultiFingerTap taps = new MultiFingerTap();
        final PinchPan pinch = new PinchPan();
        final CanvasViewport viewport = new CanvasViewport();
        boolean locked, blocked, navigating;
        int undos, redos, strokes, takenBack;
        /** The finger tool: the finger drawing or that may yet, where it landed, and whether it draws. */
        int drawFinger = -1; float downX, downY; boolean refused, drawing;
        FingerTool.Choice choice = FingerTool.Choice.ERASER;
        Pad() { viewport.configure(952, 1365, 952, 1365); }
        void run(Touch... touches) { for (Touch t : touches) event(t); }
        void event(Touch t) {
            int n = t.fingers.length;
            int[] ids = new int[n]; float[] xs = new float[n], ys = new float[n];
            for (int i = 0; i < n; i++) { ids[i] = (int) t.fingers[i][0]; xs[i] = t.fingers[i][1]; ys[i] = t.fingers[i][2]; }
            // historyTap
            int tapped = taps.event(t.action, t.time, ids, xs, ys, PinchPan.SLOP_DP);
            if (tapped != 0) { end(); if (tapped == 2) undos++; else redos++; return; }
            // fingerStroke
            if (t.action == 0) { drawFinger = ids[0]; downX = xs[0]; downY = ys[0]; drawing = false; refused = FingerTool.refused(choice, true, false, false, 60); }
            if (drawFinger >= 0 && !refused && FingerTool.cancels(t.action == 5, true, 60, drawing)) { if (drawing) takenBack++; drawing = false; refused = true; }
            if (drawFinger >= 0 && !refused && !drawing && FingerTool.starts((float) Math.hypot(xs[0] - downX, ys[0] - downY))) { drawing = true; strokes++; }
            if (t.action == 1 || t.action == 3) drawFinger = -1;
            // navigationGesture
            if (t.action == 0) { blocked = false; end(); }
            if (t.action == 1 || t.action == 3) { end(); return; }
            if (blocked) return;
            if (t.action == 6) { blocked = true; end(); return; }
            if (!pinch.tracking()) {
                if (t.action == 5 && n == 2 && !pinch.land(locked, ids[0], xs[0], ys[0], ids[1], xs[1], ys[1], PinchPan.LEAST_SPAN_DP)) blocked = true;
                return;
            }
            if (t.action == 5) { if (!navigating) { blocked = true; end(); } return; }
            if (t.action != 2) return;
            if (!navigating) {
                if (!pinch.starts(xs[0], ys[0], xs[1], ys[1], PinchPan.SLOP_DP)) return;
                taps.reset(); navigating = true;
            }
            float[] g = pinch.step(xs[0], ys[0], xs[1], ys[1], PinchPan.LEAST_SPAN_DP);
            if (g != null) viewport.gesture(g[0], g[1], g[2], g[3], g[4]);
        }
        void end() { pinch.end(); navigating = false; }
        boolean moved() { return viewport.percent() != 100 || viewport.x != 0 || viewport.y != 0; }
    }
    /** Two fingers landing 10 ms apart, each moving {@code dx, dy} (the second {@code spread} further apart) over {@code ms}, then lifting. */
    static Touch[] two(float dx, float dy, float spread, long ms) {
        java.util.List<Touch> t = new java.util.ArrayList<>();
        t.add(new Touch(0, 1000, new float[]{0, 400, 600}));
        t.add(new Touch(5, 1010, new float[]{0, 400, 600}, new float[]{1, 500, 640}));
        for (int k = 1; k <= 8; k++) {
            float u = k / 8f;
            t.add(new Touch(2, 1010 + ms * k / 8, new float[]{0, 400 + dx * u - spread * u / 2, 600 + dy * u}, new float[]{1, 500 + dx * u + spread * u / 2, 640 + dy * u}));
        }
        float[] a = {0, 400 + dx - spread / 2, 600 + dy}, b = {1, 500 + dx + spread / 2, 640 + dy};
        t.add(new Touch(6, 1020 + ms, a, b));
        t.add(new Touch(1, 1030 + ms, a));
        return t.toArray(new Touch[0]);
    }
    static void slops() {
        check(PinchPan.SLOP_DP > FingerTool.SLOP_DP, "Two fingers navigate only past the tap's slop, wider than a finger's before it draws");
        for (float d = 0; d <= 40; d += 1) {
            MultiFingerTap tap = new MultiFingerTap();
            tap.event(0, 0, new int[]{0}, new float[]{0}, new float[]{0}, PinchPan.SLOP_DP);
            tap.event(5, 10, new int[]{0, 1}, new float[]{0, 100}, new float[]{0, 0}, PinchPan.SLOP_DP);
            tap.event(2, 20, new int[]{0, 1}, new float[]{d, 100}, new float[]{0, 0}, PinchPan.SLOP_DP);
            tap.event(6, 30, new int[]{0, 1}, new float[]{d, 100}, new float[]{0, 0}, PinchPan.SLOP_DP);
            boolean isTap = tap.event(1, 40, new int[]{0}, new float[]{d}, new float[]{0}, PinchPan.SLOP_DP) == 2;
            PinchPan p = new PinchPan();
            p.land(false, 0, 0, 0, 1, 100, 0, PinchPan.LEAST_SPAN_DP);
            boolean pinches = p.starts(d, 0, 100, 0, PinchPan.SLOP_DP);
            check(isTap != pinches, "A finger moved " + d + " dp: " + (isTap ? "a tap" : "a pinch") + ", never both, never neither");
        }
    }
    static void taps() {
        for (float jitter : new float[]{0, 6, 12, 17}) {
            Pad pad = new Pad(); pad.locked = false;
            pad.run(two(jitter * .6f, -jitter * .8f, 0, 120));
            check(pad.undos == 1 && !pad.moved(), "Unlocked, a quick two-finger tap drifting " + jitter + " dp undoes and the page stays where it was");
            Pad squeeze = new Pad(); squeeze.locked = false;
            squeeze.run(two(0, 0, jitter * 2, 120));
            check(squeeze.undos == 1 && !squeeze.moved(), "and one squeezing or spreading " + jitter + " dp a finger undoes too");
        }
        Pad slow = new Pad(); slow.locked = false;
        slow.run(two(0, 0, 0, 600));
        check(slow.undos == 0 && !slow.moved(), "Two fingers resting still past a tap's time: nothing");
    }
    static void pinches() {
        Pad zoom = new Pad(); zoom.locked = false;
        zoom.run(two(0, 0, 300, 300));
        check(zoom.undos == 0 && zoom.viewport.percent() > 300, "Two fingers spreading 300 dp zoom in (" + zoom.viewport.percent() + "%), no Undo");
        Pad quick = new Pad(); quick.locked = false;
        quick.run(two(0, 0, 60, 100));
        check(quick.undos == 0 && quick.viewport.percent() > 100, "A quick small pinch, past the slop within a tap's time, zooms (" + quick.viewport.percent() + "%) and doesn't undo");
        Pad pan = new Pad(); pan.locked = false;
        pan.run(two(0, 0, 300, 300));
        float x = pan.viewport.x, y = pan.viewport.y;
        pan.run(two(-120, -200, 0, 300));
        check(pan.undos == 0 && pan.viewport.percent() == zoom.viewport.percent() && near(pan.viewport.x, x - 120, .5f) && near(pan.viewport.y, y - 200, .5f),
                "Then a two-finger drag pans by as much, the zoom kept, no Undo (" + (pan.viewport.x - x) + ", " + (pan.viewport.y - y) + ")");
        Pad fit = new Pad(); fit.locked = false;
        fit.run(two(-120, -200, 0, 300));
        check(!fit.moved() && fit.undos == 0, "A drag at 100%: the fitted page doesn't move, and no Undo");
    }
    static void threeFingers() {
        Pad pad = new Pad(); pad.locked = false;
        pad.run(new Touch(0, 1000, new float[]{0, 400, 600}),
                new Touch(5, 1010, new float[]{0, 400, 600}, new float[]{1, 500, 640}),
                new Touch(5, 1020, new float[]{0, 400, 600}, new float[]{1, 500, 640}, new float[]{2, 600, 600}),
                new Touch(2, 1040, new float[]{0, 405, 603}, new float[]{1, 504, 642}, new float[]{2, 598, 604}),
                new Touch(6, 1090, new float[]{0, 405, 603}, new float[]{1, 504, 642}, new float[]{2, 598, 604}),
                new Touch(6, 1095, new float[]{0, 405, 603}, new float[]{1, 504, 642}),
                new Touch(1, 1100, new float[]{0, 405, 603}));
        check(pad.redos == 1 && pad.undos == 0 && !pad.moved(), "Unlocked, a three-finger tap redoes and nothing moves");
        Pad wide = new Pad(); wide.locked = false;
        wide.run(new Touch(0, 1000, new float[]{0, 400, 600}),
                new Touch(5, 1010, new float[]{0, 400, 600}, new float[]{1, 500, 640}),
                new Touch(5, 1020, new float[]{0, 400, 600}, new float[]{1, 500, 640}, new float[]{2, 600, 600}),
                new Touch(2, 1040, new float[]{0, 300, 500}, new float[]{1, 600, 740}, new float[]{2, 700, 700}),
                new Touch(1, 1100, new float[]{0, 300, 500}));
        check(!wide.moved() && wide.redos == 0, "Three fingers moving: neither a pinch nor a tap");
        Pad close = new Pad(); close.locked = false;
        close.run(new Touch(0, 1000, new float[]{0, 400, 600}),
                new Touch(5, 1010, new float[]{0, 400, 600}, new float[]{1, 410, 610}),
                new Touch(2, 1100, new float[]{0, 300, 600}, new float[]{1, 700, 610}),
                new Touch(1, 1200, new float[]{0, 300, 600}));
        check(!close.moved(), "Two fingers landing closer than " + PinchPan.LEAST_SPAN_DP + " dp (one broad touch) never navigate");
    }
    static void lock() {
        Pad pad = new Pad();
        pad.locked = true;
        pad.run(two(0, 0, 300, 300));
        pad.run(two(-120, -200, 0, 300));
        check(!pad.moved() && pad.undos == 0, "Locked: a pinch and a drag move nothing");
        pad.run(two(5, 5, 4, 100));
        check(pad.undos == 1, "Locked: a two-finger tap still undoes");
        Pad view = new Pad(); view.locked = false;
        view.run(two(0, 0, 300, 300));
        float scale = view.viewport.scale(), x = view.viewport.x, y = view.viewport.y;
        view.locked = true;
        view.run(two(-120, -200, 200, 300));
        check(view.viewport.scale() == scale && view.viewport.x == x && view.viewport.y == y, "Locking keeps the view as it is, zoomed and panned");
        check(!new PinchPan().land(true, 0, 0, 0, 1, 400, 0, PinchPan.LEAST_SPAN_DP), "Locked, two fingers never land as a pinch");
    }
    static void fingerTool() {
        // The first finger drifts far enough to draw before the second lands: its stroke is taken back, and the two pinch.
        Pad pad = new Pad(); pad.locked = false;
        pad.run(new Touch(0, 1000, new float[]{0, 400, 600}),
                new Touch(2, 1030, new float[]{0, 412, 600}),
                new Touch(5, 1040, new float[]{0, 412, 600}, new float[]{1, 520, 640}),
                new Touch(2, 1100, new float[]{0, 380, 590}, new float[]{1, 560, 660}),
                new Touch(2, 1200, new float[]{0, 300, 560}, new float[]{1, 650, 700}),
                new Touch(6, 1300, new float[]{0, 300, 560}, new float[]{1, 650, 700}),
                new Touch(1, 1310, new float[]{0, 300, 560}));
        check(pad.strokes == 1 && pad.takenBack == 1 && pad.viewport.percent() > 150 && pad.undos == 0,
                "A finger's stroke is taken back when the second finger lands, and the two then pinch (" + pad.viewport.percent() + "%)");
        Pad one = new Pad(); one.locked = false;
        one.run(new Touch(0, 1000, new float[]{0, 400, 600}), new Touch(2, 1100, new float[]{0, 500, 700}), new Touch(1, 1200, new float[]{0, 500, 700}));
        check(one.strokes == 1 && one.takenBack == 0 && !one.moved(), "Unlocked, one finger still draws with the finger tool and never pans");
    }
    static void words() {
        check(PinchPan.caption(100).equals("100%") && PinchPan.caption(800).equals("800%"), "The button's percentage");
        check(PinchPan.describe(200, true).equals("Zoom 200%, locked") && PinchPan.describe(200, false).equals("Zoom 200%, unlocked"), "Its screen-reader words");
        String s = PinchPan.describe(800, false);
        check(!s.matches(".*(you|→|=|;).*"), s + ": no \"you\", no symbols as grammar");
    }

    static boolean near(float a, float b, float within) { return Math.abs(a - b) <= within; }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
