package io.github.mpdairy.monopaint;

import java.util.ArrayList;
import java.util.List;

/**
 * Spiro (mischief-lab-2.html's third pad; {@link SpiroStamper}): along the line the curve rides the stroke within its
 * width, Loops per cm setting how often it turns; in a ring the stroke's first point pins the ring, a setup's rim (a
 * second sample of no pressure, out from the pin) sizes it (the guide following the setup's drag), circling turns the
 * wheel only forward (a hand's tremble holds it still), and a tap draws the whole figure at the brush's size; Control's drives change its
 * shape (pressure sets size; a tilt the pen doesn't give changes nothing); batched otherwise the same line; never quick;
 * within the desktop budget a sample; codes, words, rolls, the dials each curve shows.
 */
public final class SpiroChecks {
    public static void main(String[] args) {
        line(); batched(); ring(); setUp(); tremble(); tap(); guide(); tracks(); drives(); curves(); media(); codes(); words(); quick(); bench();
        System.out.println("PASS: a Spiro along the line loops within its width, Loops per cm setting how often; in a ring the first point pins it, circling turns the wheel only forward "
                + "through a hand's tremble, a setup's rim sizes the ring (a pressed second sample doesn't), and a tap draws the whole figure at the brush's size, its pinned ring shown dashed over the page, following the setup's drag, and never in it; pressure sets size, a missing tilt changes nothing; batched otherwise the same line; every curve draws; "
                + "on an oval or bar track set up corner to corner the wheel rolls inside it, one unbroken line, closing after its whole turns, its guide the track; "
                + "never quick; codes round-trip, every other code byte for byte; never rolled; its words; the dials each curve shows; within the desktop budget");
    }

    static final int W = 900, H = 700;

    /** A Spiro brush: ink, Instant, steady, its tip's {@code dials} (pairs of a dial and its value). */
    static ModularBrush spiro(Object... dials) {
        ModularBrush b = StippleChecks.mk(PartChoice.SPIRO, PartChoice.STEADY, dials);
        b.set(BrushPart.LIFE, new ModularBrush.Part(PartChoice.NONE));
        return b.normalize();
    }
    static ModularBrush ring(int curve) { return spiro(PartDial.SPIRO_MODE, 1, PartDial.CURVE, curve); }
    /** A Spiro on an Oval's or Bar's track ({@code mode}), the toy's curve and wheel {@code wheel} (an index into {@link SpiroStamper#WHEELS}). */
    static ModularBrush track(int mode, int wheel) { return spiro(PartDial.SPIRO_MODE, mode, PartDial.WHEEL, wheel); }

    /** {@code path} through the stamper alone, {@code per} samples a time, into a fresh buffer; its state after in {@code meta}. */
    static FloatTiles stamp(ModularBrush b, double size, List<double[]> path, int per, LineStamper.Meta meta) {
        ResolvedBrush r = ResolvedBrush.of(b, size, 1);
        FloatTiles t = new FloatTiles(W, H, 0);
        StrokePoints pts = new StrokePoints();
        int fed = 0;
        for (double[] p : path) {
            pts.add((float) p[0], (float) p[1], (float) p[2], (long) p[3], p.length > 4 ? (float) p[4] : Float.NaN, p.length > 5 ? (float) p[5] : Float.NaN);
            if (++fed % per == 0) SpiroStamper.stamp(t, null, r.pattern, pts, meta, false);
        }
        SpiroStamper.stamp(t, null, r.pattern, pts, meta, true);
        return t;
    }
    static FloatTiles stamp(ModularBrush b, double size, List<double[]> path) { return stamp(b, size, path, 1, new LineStamper.Meta()); }

    /** A straight line from {@code x0} to {@code x1} at {@code y}, pressure {@code p}, a sample every 3 px, 2 ms apart. */
    static List<double[]> straight(double x0, double x1, double y, double p) {
        List<double[]> o = new ArrayList<>();
        int n = (int) Math.ceil((x1 - x0) / 3);
        for (int i = 0; i <= n; i++) o.add(new double[]{x0 + (x1 - x0) * i / n, y, p, 1000 + 2 * i});
        return o;
    }
    /**
     * The pen circled {@code turns} times round {@code cx, cy} at radius {@code R}, from a touch-down at the centre out
     * along the first radius, every 8 ms, jittered up to {@code jitter} px (seeded).
     */
    static List<double[]> circle(double cx, double cy, double R, double turns, double jitter) {
        List<double[]> o = new ArrayList<>();
        java.util.Random random = new java.util.Random(5);
        long t = 1000;
        o.add(new double[]{cx, cy, .7, t});
        for (int i = 1; i <= 12; i++) o.add(new double[]{cx + R * i / 12, cy, .7, t += 12});
        int n = (int) Math.round(turns * 120);
        for (int i = 1; i <= n; i++) {
            double a = i / 120.0 * 2 * Math.PI, jx = (random.nextDouble() * 2 - 1) * jitter, jy = (random.nextDouble() * 2 - 1) * jitter;
            o.add(new double[]{cx + R * Math.cos(a) + jx, cy + R * Math.sin(a) + jy, .7, t += 8});
        }
        return o;
    }
    /**
     * The drawing screen's setup (DrawingPad.RingSetup and feed): the centre {@code cx, cy} and a rim {@code R} out, of no
     * pressure, two and one {@link StrokeLog#MOST_PAUSE} before the pen, which circles {@code turns} times at {@code penR}.
     */
    static List<double[]> setUp(double cx, double cy, double R, double penR, double turns) { return setUp(cx, cy, cx + R, cy, cx, cy, penR, turns); }
    /** The setup from {@code px, py} to {@code x, y} (an Oval's or Bar's corners), the pen then circling {@code cx, cy}. */
    static List<double[]> setUp(double px, double py, double x, double y, double cx, double cy, double penR, double turns) {
        List<double[]> round = circle(cx, cy, penR, turns, 0), o = new ArrayList<>();
        double[] first = round.get(13);
        o.add(new double[]{px, py, first[2], first[3] - 2 * StrokeLog.MOST_PAUSE});
        o.add(new double[]{x, y, 0, first[3] - StrokeLog.MOST_PAUSE});
        o.addAll(round.subList(13, round.size()));
        return o;
    }
    /** The ink's box in {@code t}: {x0, y0, x1, y1}, and how many pixels hold any. */
    static int[] box(FloatTiles t) {
        int x0 = W, y0 = H, x1 = -1, y1 = -1, n = 0;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (t.get(x, y) > .05) { n++; x0 = Math.min(x0, x); x1 = Math.max(x1, x); y0 = Math.min(y0, y); y1 = Math.max(y1, y); }
        return new int[]{x0, y0, x1, y1, n};
    }

    static void line() {
        double size = ModularSizes.SPIRO, A = size / 2;
        FloatTiles t = stamp(spiro(), size, straight(100, 800, 350, .65));
        int[] b = box(t);
        check(b[4] > 3000 && b[1] >= 350 - 1.3 * A - 4 && b[3] <= 350 + 1.3 * A + 4 && b[3] - b[1] > A, "along the line, within its width: rows " + b[1] + " to " + b[3] + ", " + b[4] + " px");
        // Loops per cm: how often the curve comes back across the line's top.
        int few = crossings(stamp(spiro(PartDial.LOOPS, 1), size, straight(100, 800, 350, .65)), 350 - 40), many = crossings(stamp(spiro(PartDial.LOOPS, 3), size, straight(100, 800, 350, .65)), 350 - 40);
        check(many > 2 * few && few >= 4, "Loops per cm 1, " + few + " crossings; 3, " + many);
        // A fine line, not a band: most of the box is paper.
        check(b[4] < .5 * (b[2] - b[0] + 1) * (b[3] - b[1] + 1), "a fine line, mostly paper between: " + b[4] + " px of " + (b[2] - b[0] + 1) * (b[3] - b[1] + 1));
    }
    /** How many times row {@code y} goes from paper to ink. */
    static int crossings(FloatTiles t, int y) {
        int n = 0; boolean was = false;
        for (int x = 0; x < W; x++) { boolean on = t.get(x, y) > .3; if (on && !was) n++; was = on; }
        return n;
    }

    static void batched() {
        for (ModularBrush b : new ModularBrush[]{spiro(), ring(SpiroStamper.SPIROGRAPH), ring(SpiroStamper.HARMONOGRAPH), track(SpiroStamper.OVAL, 4), track(SpiroStamper.BAR, 9)}) {
            List<double[]> path = b.part(BrushPart.TIP).get(PartDial.SPIRO_MODE) != 0 ? circle(450, 350, 120, 2, 1) : straight(100, 800, 350, .65);
            FloatTiles one = stamp(b, ModularSizes.SPIRO, path, 1, new LineStamper.Meta()), five = stamp(b, ModularSizes.SPIRO, path, 5, new LineStamper.Meta());
            int differ = 0;
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (one.get(x, y) != five.get(x, y)) differ++;
            check(differ == 0, ModularWords.choiceWords(b, BrushPart.TIP) + ": however the samples are batched, the same line (" + differ + " px differ)");
        }
    }

    static void ring() {
        double size = ModularSizes.SPIRO, R = size / 2 * SpiroStamper.RING;
        LineStamper.Meta meta = new LineStamper.Meta();
        FloatTiles t = stamp(ring(SpiroStamper.SPIROGRAPH), size, circle(450, 350, 150, 5, 0), 1, meta);
        int[] b = box(t);
        // The rosette round the pin, within the ring: in every tenth of a turn.
        boolean all = true;
        for (int k = 0; k < 10; k++) {
            boolean any = false;
            for (int y = 0; y < H && !any; y++) for (int x = 0; x < W && !any; x++) {
                double a = Math.atan2(y + .5 - 350, x + .5 - 450);
                if (t.get(x, y) > .3 && (int) Math.floor((a + Math.PI) / (2 * Math.PI) * 10) % 10 == k) any = true;
            }
            all &= any;
        }
        check(b[0] >= 450 - R * 1.3 - 4 && b[2] <= 450 + R * 1.3 + 4 && b[1] >= 350 - R * 1.3 - 4 && b[3] <= 350 + R * 1.3 + 4 && all && b[4] > 5000,
                "a ring's rosette round the pin, within the ring, all the way round: " + b[0] + ", " + b[1] + " to " + b[2] + ", " + b[3] + "; " + b[4] + " px");
        // Five turns of the pen, five of the wheel's trip.
        SpiroStamper.State s = meta.spiro;
        check(Math.abs(s.th - s.th0 - 5 * 2 * Math.PI) < .3 && s.dir == 1, "five turns of the pen turn the wheel five trips: " + (s.th - s.th0) / (2 * Math.PI));
    }

    /** The box round {@code boxes}, and their area summed. */
    static int[] union(List<int[]> boxes) {
        int[] u = boxes.get(0).clone();
        for (int[] b : boxes) { u[0] = Math.min(u[0], b[0]); u[1] = Math.min(u[1], b[1]); u[2] = Math.max(u[2], b[2]); u[3] = Math.max(u[3], b[3]); }
        return u;
    }
    static long area(List<int[]> boxes) { long n = 0; for (int[] b : boxes) n += (long) (b[2] - b[0]) * (b[3] - b[1]); return n; }

    static void setUp() {
        // The setup's rim sizes the ring, wherever the pen then circles: the figure's reach follows it, each curve's own share of its ring.
        for (int c : new int[]{SpiroStamper.SPIROGRAPH, SpiroStamper.EPI, SpiroStamper.ROSE, SpiroStamper.LISSAJOUS, SpiroStamper.COIL}) for (double R : new double[]{80, 150}) {
            LineStamper.Meta meta = new LineStamper.Meta();
            ModularBrush b = ring(c);
            FloatTiles t = stamp(b, ModularSizes.SPIRO, setUp(450, 350, R, R == 80 ? 140 : 100, 1.5), 1, meta);
            SpiroStamper.State M = meta.spiro;
            SpiroStamper.Gear g = M.g;
            // Where the figure reaches, page px from the pin: the toy's a + d of its radius, the rest all of it (a Lissajous across its box).
            double A = M.A, want = c == SpiroStamper.SPIROGRAPH || c == SpiroStamper.EPI ? A * (g.a + g.d) : A, reach = 0;
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (t.get(x, y) > .3) {
                double dx = Math.abs(x + .5 - 450), dy = Math.abs(y + .5 - 350);
                reach = Math.max(reach, c == SpiroStamper.LISSAJOUS ? Math.max(dx, dy) : Math.hypot(dx, dy));
            }
            double ringR = A * SpiroStamper.ringShare(ResolvedBrush.of(b, ModularSizes.SPIRO, 1).pattern.spiro, M.f);
            check(Math.abs(ringR - R) < .5 && Math.abs(reach - want) < 4,
                    ModularWords.choiceWords(b, BrushPart.TIP) + " set to " + R + " px: its ring " + String.format("%.1f", ringR) + ", the figure reaches " + String.format("%.1f", reach) + " of " + String.format("%.1f", want));
        }
        // Play's Spiro (the Spirograph) sets its ring's size too (the owner, 2026-10-25: "impossible to choose a circle
        // size"; it was along the line, no ring to size).
        ModularBrush play = ToolKits.preset("Spirograph").brush().parts;
        LineStamper.Meta pm = new LineStamper.Meta();
        stamp(play, ModularSizes.SPIRO, setUp(450, 350, 120, 120, 1.5), 1, pm);
        check(play.part(BrushPart.TIP).get(PartDial.SPIRO_MODE) == 1 && Math.abs(pm.spiro.rim - 120) < .5
                        && ModularStroke.start(InkWashLayers.blank(W, H), play, ModularSizes.SPIRO, 1, null).ringGuide(ToolSettings.pressure(1), Float.NaN, Float.NaN) > 0,
                "Play's Spiro is a Ring, its ring shown and set to the setup's 120 px: " + pm.spiro.rim);
        // Only a rim of no pressure, out from the pin: one pressed (a finger's, Brush Lab's test page), or one within the
        // pin (a setup that came back in), leaves the ring at the brush's size, as a stroke with no setup.
        int[] own = box(stamp(ring(SpiroStamper.SPIROGRAPH), ModularSizes.SPIRO, circle(450, 350, 140, 1.5, 0)));
        List<double[]> pressed = setUp(450, 350, 60, 140, 1.5), inside = setUp(450, 350, 10, 140, 1.5);
        pressed.get(1)[2] = .7;
        LineStamper.Meta pr = new LineStamper.Meta(), in = new LineStamper.Meta();
        int[] a = box(stamp(ring(SpiroStamper.SPIROGRAPH), ModularSizes.SPIRO, pressed, 1, pr)), b = box(stamp(ring(SpiroStamper.SPIROGRAPH), ModularSizes.SPIRO, inside, 1, in));
        check(pr.spiro.rim == 0 && in.spiro.rim == 0 && Math.abs((a[2] - a[0]) - (own[2] - own[0])) <= 2 && Math.abs((b[2] - b[0]) - (own[2] - own[0])) <= 2,
                "a pressed rim, or one within the pin, leaves the brush's size: " + (a[2] - a[0]) + ", " + (b[2] - b[0]) + " px across, as " + (own[2] - own[0]));
        // A move that never leaves the pin is a tap: the whole figure at the brush's size.
        List<double[]> wiggle = new ArrayList<>();
        for (int i = 0; i < 6; i++) wiggle.add(new double[]{450 + (i % 2) * 8, 350 + (i / 2) * 3, .7, 1000 + 8 * i});
        int[] tap = box(stamp(ring(SpiroStamper.SPIROGRAPH), ModularSizes.SPIRO, java.util.Collections.singletonList(new double[]{450, 350, .7, 1000}))), short_ = box(stamp(ring(SpiroStamper.SPIROGRAPH), ModularSizes.SPIRO, wiggle));
        check(Math.abs(tap[0] - short_[0]) <= 1 && Math.abs(tap[2] - short_[2]) <= 1 && Math.abs(tap[1] - short_[1]) <= 1 && Math.abs(tap[3] - short_[3]) <= 1 && tap[2] - tap[0] > 300,
                "a move within the pin draws a tap's whole figure: " + (short_[2] - short_[0]) + " px across, a tap's " + (tap[2] - tap[0]));
    }

    static void tremble() {
        // A pen circling with ±2 px of tremble: the wheel only goes forward.
        double size = ModularSizes.SPIRO;
        for (int c = 0; c < 6; c++) {
            LineStamper.Meta meta = new LineStamper.Meta();
            ResolvedBrush r = ResolvedBrush.of(ring(c), size, 1);
            StrokePoints pts = new StrokePoints();
            FloatTiles t = new FloatTiles(W, H, 0);
            double last = Double.NaN;
            int back = 0;
            for (double[] p : circle(450, 350, 140, 3, 2)) {
                pts.add((float) p[0], (float) p[1], (float) p[2], (long) p[3], Float.NaN, Float.NaN);
                SpiroStamper.stamp(t, null, r.pattern, pts, meta, false);
                if (meta.spiro.on) { if (!Double.isNaN(last) && meta.spiro.th < last - 1e-9) back++; last = meta.spiro.th; }
            }
            check(back == 0 && meta.spiro.th - meta.spiro.th0 > 2.8 * 2 * Math.PI, ModularWords.choiceWords(ring(c), BrushPart.TIP) + ": a tremble never turns the wheel back (" + back + "), " + (meta.spiro.th - meta.spiro.th0) / (2 * Math.PI) + " trips");
        }
        // Turned back well past a tremble, the wheel turns back.
        LineStamper.Meta meta = new LineStamper.Meta();
        List<double[]> path = circle(450, 350, 140, 1, 0);
        double[] end = path.get(path.size() - 1);
        long t = (long) end[3];
        for (int i = 1; i <= 60; i++) { double a = -i / 120.0 * 2 * Math.PI; path.add(new double[]{450 + 140 * Math.cos(a), 350 + 140 * Math.sin(a), .7, t += 8}); }
        stamp(ring(SpiroStamper.SPIROGRAPH), size, path, 1, meta);
        check(meta.spiro.dir == -1 && meta.spiro.th - meta.spiro.th0 < .65 * 2 * Math.PI, "turned back past a tremble, the wheel turns back: " + (meta.spiro.th - meta.spiro.th0) / (2 * Math.PI));
    }

    static void tap() {
        // A tap in a ring draws the whole figure round the pin; a tap along the line, a mark.
        ToneDocument d = InkWashLayers.blank(W, H);
        DrawingStroke s = ModularStroke.start(d, ring(SpiroStamper.SPIROGRAPH), ModularSizes.SPIRO, 1, null);
        CarrySmudgeChecks.feed(s, new double[][]{{450, 350, .7, 1000}});
        int ink = 0, x0 = W, x1 = 0, y0 = H, y1 = 0;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (d.compositeTone(x, y) < 200) { ink++; x0 = Math.min(x0, x); x1 = Math.max(x1, x); y0 = Math.min(y0, y); y1 = Math.max(y1, y); }
        double R = ModularSizes.SPIRO / 2 * SpiroStamper.RING;
        check(ink > 8000 && x1 - x0 > 1.2 * R && y1 - y0 > 1.2 * R && Math.abs((x0 + x1) / 2.0 - 450) < 8 && Math.abs((y0 + y1) / 2.0 - 350) < 8,
                "a tap in a ring draws the whole rosette round it: " + ink + " px, " + (x1 - x0 + 1) + " × " + (y1 - y0 + 1));
        // The whole figure closes: the spirograph's 96 / 40 in 5 trips of the ring.
        ResolvedBrush r = ResolvedBrush.of(ring(SpiroStamper.SPIROGRAPH), ModularSizes.SPIRO, 1);
        check(Math.abs(SpiroStamper.span(r.pattern.spiro, null) - 5 * 2 * Math.PI) < 1e-9, "the toy's 96-tooth ring and 40-tooth wheel close in five trips");
        ToneDocument dot = InkWashLayers.blank(W, H);
        DrawingStroke l = ModularStroke.start(dot, spiro(), ModularSizes.SPIRO, 1, null);
        CarrySmudgeChecks.feed(l, new double[][]{{450, 350, .7, 1000}});
        int marks = 0;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (dot.compositeTone(x, y) < 250) marks++;
        check(marks > 0 && marks < 400, "a tap along the line, a small mark: " + marks + " px");
    }

    static void guide() {
        // The ring's radius: the stroke's own at its touch-down's pressure; none along the line.
        double R = ModularSizes.SPIRO / 2 * SpiroStamper.RING;
        DrawingStroke ring = ModularStroke.start(InkWashLayers.blank(W, H), ring(SpiroStamper.SPIROGRAPH), ModularSizes.SPIRO, 1, null);
        float firm = ring.ringGuide(ToolSettings.pressure(1), Float.NaN, Float.NaN), light = ring.ringGuide(ToolSettings.pressure(.2f), Float.NaN, Float.NaN);
        DrawingStroke line = ModularStroke.start(InkWashLayers.blank(W, H), spiro(), ModularSizes.SPIRO, 1, null);
        check(Math.abs(firm - 1.3 * R) < 1 && Math.abs(light - .58 * R) < 1 && line.ringGuide(ToolSettings.pressure(1), 0, 0) == 0
                && Math.abs(ring(SpiroStamper.EPI).part(BrushPart.TIP).get(PartDial.CURVE) - SpiroStamper.EPI) < 1e-9
                && ModularStroke.start(InkWashLayers.blank(W, H), ring(SpiroStamper.EPI), ModularSizes.SPIRO, 1, null).ringGuide(ToolSettings.pressure(1), Float.NaN, Float.NaN) < firm,
                "the ring's radius follows the touch-down's pressure (" + light + " to " + firm + " px), the epitrochoid's inside its wheel; none along the line");
        // Shown over the page in light grey (darker marks show through), never in it; gone at the lift.
        ToneDocument d = InkWashLayers.blank(W, H);
        MirrorShow show = new MirrorShow();
        show.page(W, H);
        show.ring(450, 350, 200, 1);
        java.util.List<int[]> drawn = show.take(null, null);
        int[] px = new int[W * H];
        java.util.Arrays.fill(px, 0xffffffff);
        show.draw(px, 0, 0, W, H, false, (x, y) -> x == 650 && y == 350 ? 0 : 255, 255);
        int grey = 0, other = 0;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            int v = px[y * W + x] & 0xff;
            if (v == Mirrors.PLACING_TONE) grey++; else if (v != 255 && !(x == 650 && y == 350)) other++;
        }
        boolean page = true;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) page &= d.compositeTone(x, y) == 255;
        // Dashed: round the circle, about half of it paper.
        int gaps = 0;
        for (int i = 0; i < 720; i++) { double a = i * Math.PI / 360; if ((px[(int) (350 + 200 * Math.sin(a)) * W + (int) (450 + 200 * Math.cos(a))] & 0xff) == 255) gaps++; }
        int[] all = union(drawn);
        check(grey > Math.PI * 200 && grey < 2 * 2 * Math.PI * 200 && gaps > 200 && gaps < 520 && other == 0 && (px[350 * W + 650] & 0xff) == 0 && page && show.showing()
                && all[0] <= 250 && all[2] >= 650 && all[1] <= 150 && all[3] >= 550 && area(drawn) < .5 * 400 * 400,
                "the ring over the page dashed in light grey, a mark under it still dark, the page untouched, only its arcs drawn: " + grey + " px, " + gaps + " of 720 gaps, " + area(drawn) + " px drawn");
        // Its end says so, the page's flash due (its redraws left ghosts on the panel); an end with no ring doesn't.
        boolean ended = show.end(), again = show.end();
        java.util.List<int[]> gone = show.take(null, null);
        java.util.Arrays.fill(px, 0xffffffff);
        check(ended && !again && !new MirrorShow().end() && !show.showing() && !gone.isEmpty() && !show.draw(px, 0, 0, W, H, false, (x, y) -> 255, 255),
                "gone at the lift, its arcs drawn again; the end says a ring went (the flash), once");
        // The setup's drag: the ring's radius is the pen's distance, out and back in, the brush's own within the pin; the
        // stamper's rim the same.
        MirrorShow pulled = new MirrorShow();
        pulled.page(W, H);
        pulled.ring(450, 350, 200, 1);
        pulled.take(null, null);
        boolean out = pulled.ringTo(570, 350), pin = pulled.ringTo(455, 352), back = pulled.ringTo(540, 350), still = !pulled.ringTo(540.2f, 350);
        LineStamper.Meta meta = new LineStamper.Meta();
        stamp(ring(SpiroStamper.SPIROGRAPH), ModularSizes.SPIRO, setUp(450, 350, 90, 140, 1), 1, meta);
        java.util.List<int[]> redrawn = pulled.take(null, null);
        java.util.Arrays.fill(px, 0xffffffff);
        pulled.draw(px, 0, 0, W, H, false, (x, y) -> 255, 255);
        int near = 0, far = 0;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if ((px[y * W + x] & 0xff) != 255) { if (Math.abs(Math.hypot(x + .5 - 450, y + .5 - 350) - 90) < 3) near++; else far++; }
        check(out && pin && back && still && near > 200 && far == 0 && !redrawn.isEmpty() && Math.abs(meta.spiro.rim - 90) < 1e-3,
                "the guide follows the drag out to 120 px, back to the brush's within the pin, in to 90, as the stamper's rim (" + meta.spiro.rim + "): " + near + " px on it, " + far + " off");
        boolean setEnded = pulled.end();
        java.util.Arrays.fill(px, 0xffffffff);
        check(setEnded && !pulled.showing() && !pulled.take(null, null).isEmpty() && !pulled.draw(px, 0, 0, W, H, false, (x, y) -> 255, 255), "a set guide gone at its end");
        // A drag's many samples between two renders draw the ring again once, as one jump would (the Manta, 2026-10-26:
        // each sample's arcs rendered one by one kept the pen's input 20 s and more behind).
        MirrorShow jump = new MirrorShow(), drag = new MirrorShow();
        for (MirrorShow m : new MirrorShow[]{jump, drag}) { m.page(W, H); m.ring(450, 350, 200, 1); m.take(null, null); }
        jump.ringTo(780, 350);
        for (int i = 1; i <= 600; i++) drag.ringTo(450 + (float) ((200 + 130 * i / 600.0) * Math.cos(i * .02)), 350 + (float) ((200 + 130 * i / 600.0) * Math.sin(i * .02)));
        drag.ringTo(780, 350);
        int jumped = jump.take(null, null).size(), dragged = drag.take(null, null).size();
        check(dragged == jumped, "a 600-sample drag between renders draws the ring again once: " + dragged + " boxes, one jump " + jumped);
    }

    static void tracks() {
        // Oval and Bar: set up corner to corner, 400 × 260 round 450, 350; the wheel rolls inside the track, the figure
        // within it and reaching near its line, one unbroken line round a Bar's corners too; replayed, the same line.
        for (int mode : new int[]{SpiroStamper.OVAL, SpiroStamper.BAR}) for (int wheel : new int[]{0, 4, 13}) {
            ModularBrush b = track(mode, wheel);
            LineStamper.Meta meta = new LineStamper.Meta();
            List<double[]> path = setUp(250, 480, 650, 220, 450, 350, 150, 3);
            FloatTiles t = stamp(b, ModularSizes.SPIRO, path, 1, meta), again = stamp(b, ModularSizes.SPIRO, path, 7, new LineStamper.Meta());
            SpiroStamper.State M = meta.spiro;
            double out = -1e9, lineR = SpiroStamper.lineR(ModularSizes.SPIRO);
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (t.get(x, y) > .05) out = Math.max(out, SpiroStamper.edge(mode, 200, 130, x + .5 - 450, y + .5 - 350));
            // The hole comes nearest the track a wheel's radius less its hole's distance in.
            double gap = M.A * (M.g.a - M.g.d);
            check(M.cx == 450 && M.cy == 350 && M.rim == 200 && M.tr.ha == 1 && Math.abs(M.tr.hb - .65) < 1e-9 && out <= lineR + 1.5 && out >= -gap - lineR - 3
                            && pieces(t) == 1 && same(t, again) && M.g.a * M.A <= M.tr.bend * M.A,
                    ModularWords.choiceWords(b, BrushPart.TIP) + ", wheel " + SpiroStamper.WHEELS[wheel] + ": inside the track, " + String.format("%.1f", out) + " px from its line at most (the hole " + String.format("%.1f", gap) + " in), " + pieces(t) + " piece, replayed the same");
            // A tap: the whole figure, closing where it began after the wheel's teeth over their common factor with the track's.
            LineStamper.Meta tm = new LineStamper.Meta();
            stamp(b, ModularSizes.SPIRO, java.util.Collections.singletonList(new double[]{450, 350, .7, 1000}), 1, tm);
            SpiroStamper.State T = tm.spiro;
            int w = SpiroStamper.WHEELS[wheel], laps = w / gcd(T.tr.teeth, w);
            double[] start = new double[3];
            SpiroStamper.at(T.g, T.th0, T.g.q * T.th0, T.A, T.cx, T.cy, 0, 0, true, start);
            check(Math.abs(T.th - T.th0 - laps * 2 * Math.PI) < 1e-6 && Math.hypot(T.hx - start[0], T.hy - start[1]) < .5,
                    ModularWords.choiceWords(b, BrushPart.TIP) + ", wheel " + w + " on " + T.tr.teeth + " teeth: closes after " + laps + " laps, " + String.format("%.3f", Math.hypot(T.hx - start[0], T.hy - start[1])) + " px from its start");
        }
        // The guide: the track's dashes corner to corner, a Bar's rounded corners, as the stamper sets it.
        MirrorShow show = new MirrorShow();
        show.page(W, H);
        show.ring(250, 480, 60, SpiroStamper.BAR);
        show.take(null, null);
        boolean moved = show.ringTo(650, 220);
        List<int[]> drawn = show.take(null, null);
        int[] px = new int[W * H];
        java.util.Arrays.fill(px, 0xffffffff);
        show.draw(px, 0, 0, W, H, false, (x, y) -> 255, 255);
        int on = 0, off = 0;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if ((px[y * W + x] & 0xff) != 255) { if (Math.abs(SpiroStamper.edge(SpiroStamper.BAR, 200, 130, x + .5 - 450, y + .5 - 350)) < 2) on++; else off++; }
        int[] all = union(drawn);
        check(moved && on > 600 && off == 0 && all[0] <= 250 && all[2] >= 650 && all[1] <= 220 && all[3] >= 480 && show.end(),
                "the Bar's guide corner to corner, dashed on its line: " + on + " px on it, " + off + " off");
        check(ModularWords.choiceWords(track(SpiroStamper.OVAL, 4), BrushPart.TIP).equals("Spirograph oval") && ModularWords.plainWords(track(SpiroStamper.BAR, 4)).startsWith("Ink spiro. " + ModularWords.SPIRO_BAR),
                "a track's words: " + ModularWords.choiceWords(track(SpiroStamper.OVAL, 4), BrushPart.TIP));
    }
    private static int gcd(int a, int b) { return b == 0 ? a : gcd(b, a % b); }
    /** How many separate pieces of ink {@code t} holds, touching diagonally too. */
    static int pieces(FloatTiles t) {
        boolean[] seen = new boolean[W * H];
        int n = 0;
        java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
        for (int i = 0; i < W * H; i++) {
            if (seen[i] || t.get(i % W, i / W) <= .05) continue;
            n++; seen[i] = true; q.add(i);
            while (!q.isEmpty()) {
                int j = q.poll(), x = j % W, y = j / W;
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                    int a = x + dx, c = y + dy;
                    if (a < 0 || c < 0 || a >= W || c >= H || seen[c * W + a] || t.get(a, c) <= .05) continue;
                    seen[c * W + a] = true; q.add(c * W + a);
                }
            }
        }
        return n;
    }

    static void drives() {
        double size = ModularSizes.SPIRO;
        // Pressure sets size (the default): a firm line reaches further from the line than a light one.
        int[] light = box(stamp(spiro(), size, straight(100, 800, 350, .15))), firm = box(stamp(spiro(), size, straight(100, 800, 350, 1)));
        check(firm[3] - firm[1] > 1.6 * (light[3] - light[1]), "pressure sets size: light " + (light[3] - light[1]) + " px across, firm " + (firm[3] - firm[1]));
        // Pressure set to Nothing: the same either way.
        ModularBrush none = spiro();
        none.part(BrushPart.HAND).set(PartDial.P_DRIVE, SpiroStamper.NOTHING);
        int[] a = box(stamp(none, size, straight(100, 800, 350, .15))), b = box(stamp(none, size, straight(100, 800, 350, 1)));
        check(a[1] == b[1] && a[3] == b[3], "pressure set to Nothing changes nothing");
        // Tilt sets Hole (the default): a pen with no tilt reading leaves it as set, upright and leaning differ.
        FloatTiles bare = stamp(spiro(), size, straight(100, 800, 350, .65));
        FloatTiles upright = stamp(spiro(), size, tilted(straight(100, 800, 350, .65), 0)), leaning = stamp(spiro(), size, tilted(straight(100, 800, 350, .65), 60));
        ModularBrush noTilt = spiro();
        noTilt.part(BrushPart.HAND).set(PartDial.T_DRIVE, SpiroStamper.NOTHING);
        FloatTiles asSet = stamp(noTilt, size, straight(100, 800, 350, .65));
        check(same(bare, asSet) && !same(upright, leaning) && !same(upright, asSet), "a missing tilt leaves Hole as set; upright and leaning differ");
        // Speed sets Loops: a fast line loops more often than a slow one, over the same length.
        ModularBrush speedy = spiro();
        speedy.part(BrushPart.HAND).set(PartDial.S_DRIVE, SpiroStamper.LOOPS);
        List<double[]> slow = straight(100, 800, 350, .65), fast = straight(100, 800, 350, .65);
        for (int i = 0; i < slow.size(); i++) { slow.get(i)[3] = 1000 + 30 * i; fast.get(i)[3] = 1000 + i; }
        LineStamper.Meta m1 = new LineStamper.Meta(), m2 = new LineStamper.Meta();
        stamp(speedy, size, slow, 1, m1);
        stamp(speedy, size, fast, 1, m2);
        double l1 = m1.spiro.th / (2 * Math.PI), l2 = m2.spiro.th / (2 * Math.PI);
        check(l2 > 2 * l1, "speed sets loops: slow " + String.format("%.1f", l1) + " loops, fast " + String.format("%.1f", l2));
    }
    static List<double[]> tilted(List<double[]> path, double degrees) {
        List<double[]> o = new ArrayList<>();
        for (double[] p : path) o.add(new double[]{p[0], p[1], p[2], p[3], degrees, 0});
        return o;
    }
    static boolean same(FloatTiles a, FloatTiles b) {
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (a.get(x, y) != b.get(x, y)) return false;
        return true;
    }

    static void curves() {
        // Every curve draws in both modes, and its far side, a coil's, lighter.
        for (int c = 0; c < 6; c++) for (int mode = 0; mode < 4; mode++) {
            ModularBrush b = spiro(PartDial.SPIRO_MODE, mode, PartDial.CURVE, c);
            int[] box = box(stamp(b, ModularSizes.SPIRO, mode != 0 ? circle(450, 350, 140, 3, 0) : straight(100, 800, 350, .65)));
            check(box[4] > 1500, ModularWords.choiceWords(b, BrushPart.TIP) + " draws: " + box[4] + " px");
        }
        FloatTiles coil = stamp(spiro(PartDial.CURVE, SpiroStamper.COIL), ModularSizes.SPIRO, straight(100, 800, 350, .65));
        int far = 0, near = 0;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) { float v = coil.get(x, y); if (v > .9) near++; else if (v > .44 && v < .48) far++; }
        check(far > 200 && near > 200, "a coil's far side lighter: " + far + " px light, " + near + " dark");
        // The dials each curve shows: the toy's its wheel, a rose its petals, the swings their ratio; Loops along the line.
        ModularBrush.Part t = spiro().part(BrushPart.TIP);
        check(SpiroStamper.shows(t, PartDial.WHEEL) && SpiroStamper.shows(t, PartDial.LOOPS) && !SpiroStamper.shows(t, PartDial.PETALS) && !SpiroStamper.shows(t, PartDial.RATIO), "the toy shows its wheel");
        ModularBrush.Part rose = ring(SpiroStamper.ROSE).part(BrushPart.TIP), harmo = ring(SpiroStamper.HARMONOGRAPH).part(BrushPart.TIP);
        check(SpiroStamper.shows(rose, PartDial.PETALS) && !SpiroStamper.shows(rose, PartDial.WHEEL) && !SpiroStamper.shows(rose, PartDial.LOOPS)
                && SpiroStamper.shows(harmo, PartDial.FADES) && SpiroStamper.shows(harmo, PartDial.PHASE) && !SpiroStamper.shows(harmo, PartDial.HOLE), "a rose its petals, a harmonograph its fade, no loops in a ring");
        BrushLab lab = new BrushLab(new ToolSlots(StampChecks.mapStore(new java.util.HashMap<>()), new java.util.Random(3)), 0, new java.util.Random(4));
        lab.choose(BrushPart.TIP, PartChoice.SPIRO);
        check(lab.dials(BrushPart.TIP).size() == 8 && !lab.dials(BrushPart.TIP).contains(PartDial.PETALS), "Brush Lab shows the toy's eight dials: " + lab.dials(BrushPart.TIP));
    }

    static void media() {
        for (PartChoice m : new PartChoice[]{PartChoice.INK, PartChoice.WASH, PartChoice.COVER, PartChoice.CHARCOAL, PartChoice.GRAPHITE}) for (PartChoice life : new PartChoice[]{PartChoice.NONE, PartChoice.FAST_WET, PartChoice.DRYING}) {
            ModularBrush b = spiro();
            b.set(BrushPart.MEDIUM, new ModularBrush.Part(m));
            b.set(BrushPart.LIFE, new ModularBrush.Part(life));
            b = b.normalize();
            ToneDocument d = InkWashLayers.blank(W, H);
            DrawingStroke s = ModularStroke.start(d, b, ModularSizes.SPIRO, 1, new WatercolorSession());
            CarrySmudgeChecks.feed(s, StampChecks.line(100, 800, 350, 1000));
            int ink = 0;
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (d.compositeTone(x, y) < 250) ink++;
            check(ink > 1500, m + ", " + life + " lays the curve: " + ink + " px");
        }
    }

    static void codes() {
        ModularBrush b = ring(SpiroStamper.ROSE);
        b.part(BrushPart.TIP).set(PartDial.PETALS, 7);
        b.part(BrushPart.HAND).set(PartDial.S_DRIVE, SpiroStamper.PETALS);
        b.name = "rosette";
        String code = ModularCode.encode(b);
        ModularBrush back = ModularCode.decode(code);
        check(code.matches("rosette\\.M[0-9a-z]b.*") && back != null && ModularCode.encode(back).equals(code) && back.choice(BrushPart.TIP) == PartChoice.SPIRO
                && (int) back.part(BrushPart.TIP).get(PartDial.PETALS) == 7 && (int) back.part(BrushPart.HAND).get(PartDial.S_DRIVE) == SpiroStamper.PETALS, "a Spiro's code is Tip choice 11 and reads back: " + code);
        // The drives at their defaults write nothing: every hand's code is as before.
        ModularBrush plain = StippleChecks.mk(PartChoice.ROUND, PartChoice.PRESS_BOTH);
        String hand = ModularCode.encode(plain).split("\\.")[1].split("_")[5];
        check(hand.length() == 1 + 9 * 2 + 2 + 1, "a hand with the drives as set writes the nine, gain and dir: " + hand);
        for (ToolPresets.Preset p : ToolPresets.all()) check((ModularCode.encode(ModularCode.decode(p.code)).equals(p.code) || HandHatchChecks.handHatch(p.code)), p.name + "'s code byte for byte");
        // A hand's preset swapped keeps its drives.
        ModularBrush.Part kept = ModularBrush.switchTo(b.part(BrushPart.HAND), PartChoice.PRESS_DARKER);
        check((int) kept.get(PartDial.S_DRIVE) == SpiroStamper.PETALS, "a Control preset keeps the drives");
        java.util.Random random = new java.util.Random(13);
        for (ModularStyle s : ModularStyle.all()) for (int i = 0; i < 200; i++) {
            ModularBrush rolled = s.roll(random);
            check(rolled.choice(BrushPart.TIP) != PartChoice.SPIRO && (int) rolled.part(BrushPart.HAND).get(PartDial.P_DRIVE) == 0, s.name + " never rolls a Spiro, nor its drives");
        }
        check(ModularFeatures.seedOnly(PartChoice.SPIRO) && !ModularFeatures.varies(PartChoice.SPIRO) && ModularFeatures.offered(PartChoice.SPIRO), "offered in Brush Lab, never a variation's swap");
    }

    static void words() {
        String plain = ModularWords.plainWords(spiro());
        check(plain.startsWith("Ink spiro. " + ModularWords.SPIRO_LINE) && plain.endsWith(" Pressure sets size, tilt sets hole."), "This brush: " + plain);
        String rose = ModularWords.plainWords(ring(SpiroStamper.ROSE));
        check(rose.startsWith("Ink spiro. " + ModularWords.SPIRO_RING) && rose.endsWith(" Pressure sets size."), "a rose has no hole: " + rose);
        ModularBrush b = ring(SpiroStamper.ROSE);
        b.part(BrushPart.HAND).set(PartDial.T_DRIVE, SpiroStamper.SIZE);
        check(ModularWords.handWords(b, 2).equals("pressure and tilt set size"), "two drives one thing: " + ModularWords.handWords(b, 2));
        check(ModularWords.caption(spiro()).contains("spiro") && ModularWords.choiceWords(spiro(), BrushPart.TIP).equals("Spirograph")
                && ModularWords.choiceWords(ring(SpiroStamper.HARMONOGRAPH), BrushPart.TIP).equals("Harmonograph ring"), "caption " + ModularWords.caption(spiro()) + ", the Tip's module " + ModularWords.choiceWords(ring(SpiroStamper.HARMONOGRAPH), BrushPart.TIP));
        ModularBrush.Part t = spiro().part(BrushPart.TIP);
        check(PartDial.WHEEL.shown(4, t).equals("40") && PartDial.HOLE.shown(.8, t).equals("80%") && PartDial.PHASE.shown(.5, t).equals("45°") && PartDial.LOOPS.shown(2, t).equals("2.0")
                && PartDial.CURVE.shown(4, t).equals("Harmonograph") && PartDial.P_DRIVE.shown(3, spiro().part(BrushPart.HAND)).equals("Hole"), "dial values");
        check(ModularSizes.size(spiro(), InkWashSizes.Preset.MEDIUM) == ModularSizes.SPIRO, "a Spiro's Medium is its own, whatever the style");
    }

    static void quick() {
        for (PartChoice m : new PartChoice[]{PartChoice.INK, PartChoice.WASH}) for (int mode = 0; mode < 2; mode++) for (InkWashSizes.Preset p : InkWashSizes.Preset.values()) {
            ModularBrush b = spiro(PartDial.SPIRO_MODE, mode);
            b.set(BrushPart.MEDIUM, new ModularBrush.Part(m));
            b = b.normalize();
            check(!QuickStroke.quick(ResolvedBrush.of(b, ModularSizes.size(b, p), 1)), m + " " + p + ": a Spiro is drawn live, never as a quick preview");
        }
    }

    static void bench() {
        // The desktop's budget: 0.1 ms a sample, a whole live stroke (stamper and compose) at the Medium size and Large.
        for (int mode = 0; mode < 4; mode++) for (double size : new double[]{ModularSizes.SPIRO, 2 * ModularSizes.SPIRO}) {
            ModularBrush b = spiro(PartDial.SPIRO_MODE, mode);
            List<double[]> path = mode != 0 ? circle(450, 350, 200, 3, 1) : straight(60, 840, 350, .65);
            double[][] arr = path.toArray(new double[0][]);
            double best = Double.MAX_VALUE;
            for (int run = 0; run < 5; run++) {
                ToneDocument d = InkWashLayers.blank(W, H);
                long t0 = System.nanoTime();
                DrawingStroke s = ModularStroke.start(d, b, size, 1, null);
                for (int i = 0; i < arr.length; i++) { s.sample((float) arr[i][0], (float) arr[i][1], ToolSettings.pressure((float) arr[i][2]), 0, 0, (long) arr[i][3]); if (i % 4 == 3) s.present(); }
                s.present(); s.finish();
                best = Math.min(best, (System.nanoTime() - t0) / 1e6 / arr.length);
            }
            Timing.limit(best <= .1, PartDial.SPIRO_MODE.choices[mode] + " at " + size + " px: " + String.format("%.3f", best) + " ms a sample, within 0.1");
        }
    }

    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
