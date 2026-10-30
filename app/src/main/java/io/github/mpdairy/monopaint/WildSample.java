package io.github.mpdairy.monopaint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A brush's sample stroke, for panel tiles, Brush Lab's cards and the bar's buttons: the app icon's loop (Phosphor
 * scribble-loop's centre line, turned to stand upright, as the icon is), light at the ends, firm through the middle and slower where firm, as the Brush
 * Lab mockup's loopPoints; or, on the drawing screen's bar, a pen in a tray: a short gentle stroke lying across a
 * row ({@link #tray}); or, on a part card, what that part adds ({@link #part}). Drawn through a real stroke
 * on a small blank page, on a clock that stands still; its pixels are the page's dot screen, as the page shows.
 * The last few are kept, so rebuilding a panel or the lab doesn't draw them again. Pure, checked on the desktop.
 */
final class WildSample {
    /**
     * How many samples are kept, the least recently asked for dropped first: the lab shows about thirty at once. They
     * hold at most {@link #KEPT_PIXELS} (16 MB). A big one-off sample can be drawn without being kept.
     */
    static final int KEPT = 64, KEPT_PIXELS = 4 << 20;
    /** The loop's centre line in the icon's 256 units: the mockup's LOOP. */
    static final int[][] LOOP = {{28, 93}, {50, 72}, {78, 55}, {108, 48}, {138, 53}, {160, 68}, {173, 88}, {180, 110}, {180, 135}, {170, 160},
            {150, 183}, {124, 200}, {100, 207}, {78, 201}, {63, 186}, {60, 167}, {68, 147}, {86, 131}, {110, 120}, {136, 112}, {160, 114},
            {184, 122}, {210, 137}, {236, 155}, {246, 161}};
    /**
     * The loop's extent in icon units, and where it starts, as drawn: {@link #LOOP} turned a quarter clockwise, then
     * flipped left to right (x and y swapped), so it no longer reads as a letter a, its tail after it crosses itself
     * half as long ({@link #LOOP_TAIL}).
     */
    private static final double LOOP_WIDTH = 159, LOOP_HEIGHT = 186, LOOP_LEFT = 48, LOOP_TOP = 28;
    /** How much of the icon's tail after the crossing the loop keeps (the owner's sketch: cut about halfway). */
    static final double LOOP_TAIL = .5;
    /** Where the loop's pressure is full, of the way along it: light at the start, firm from here to the end. */
    static final double LOOP_FIRM = .6;
    private static final Map<String, int[]> kept = new LinkedHashMap<>(KEPT, .75f, true);
    /** The pixels {@link #kept} holds. */
    private static long keptPixels;

    /** Starts the sampled stroke on a blank page, on the layer it draws on. */
    interface Maker { DrawingStroke start(ToneDocument page); }
    /** What a sample draws: the icon's loop, or the bar's tray stroke. */
    enum Shape { LOOP, TRAY }

    private WildSample() { }

    /**
     * The mockup's loopPoints: Catmull-Rom through {@link #LOOP}, x and y swapped, about every two pixels, scaled to fit
     * {@code width × height} inside {@code pad}, placed at {@code x0, y0}, its tail after the crossing shortened
     * ({@link #cut}). Each point is {x, y, pressure travel 0–1, time in ms}: light at the start, firm from {@link
     * #LOOP_FIRM} of the way to the end, slower where firm; a point under .6 px from the last is left out, as the
     * mockup's addPoint does.
     */
    static float[][] loop(float width, float height, float pad, float x0, float y0) {
        double scale = Math.min((width - 2 * pad) / LOOP_WIDTH, (height - 2 * pad) / LOOP_HEIGHT);
        double ox = x0 + (width - LOOP_WIDTH * scale) / 2 - LOOP_LEFT * scale, oy = y0 + (height - LOOP_HEIGHT * scale) / 2 - LOOP_TOP * scale;
        int n = LOOP.length;
        double[][] p = new double[n][];
        for (int i = 0; i < n; i++) p[i] = new double[]{ox + LOOP[i][1] * scale, oy + LOOP[i][0] * scale};
        List<double[]> dense = new ArrayList<>();
        for (int i = 0; i < n - 1; i++) {
            double[] p0 = p[Math.max(0, i - 1)], p1 = p[i], p2 = p[i + 1], p3 = p[Math.min(n - 1, i + 2)];
            int steps = Math.max(2, (int)Math.ceil(Math.hypot(p2[0] - p1[0], p2[1] - p1[1]) / 2));
            for (int s = 0; s < steps; s++) {
                double t = (double)s / steps, t2 = t * t, t3 = t2 * t;
                double[] q = new double[2];
                for (int k = 0; k < 2; k++)
                    q[k] = .5 * (2 * p1[k] + (-p0[k] + p2[k]) * t + (2 * p0[k] - 5 * p1[k] + 4 * p2[k] - p3[k]) * t2 + (-p0[k] + 3 * p1[k] - 3 * p2[k] + p3[k]) * t3);
                dense.add(q);
            }
        }
        dense.add(p[n - 1]);
        dense = cut(dense, LOOP_TAIL);
        List<float[]> points = new ArrayList<>();
        double time = 0, lastX = 0, lastY = 0;
        for (int i = 0; i < dense.size(); i++) {
            double u = (double)i / (dense.size() - 1), pressure = firmEnd(u);
            time += 2 + 3 * pressure;
            double x = dense.get(i)[0], y = dense.get(i)[1];
            if (!points.isEmpty() && Math.hypot(x - lastX, y - lastY) < .6) continue;
            points.add(new float[]{(float)x, (float)y, (float)pressure, (float)time});
            lastX = x; lastY = y;
        }
        return points.toArray(new float[0][]);
    }
    /**
     * {@code line} with only {@code keep} of its length after it first crosses itself, the end on the line between two
     * of its points; the whole line if it never crosses itself.
     */
    static List<double[]> cut(List<double[]> line, double keep) {
        int n = line.size();
        double[] arc = new double[n];
        for (int i = 1; i < n; i++) arc[i] = arc[i - 1] + Math.hypot(line.get(i)[0] - line.get(i - 1)[0], line.get(i)[1] - line.get(i - 1)[1]);
        for (int k = 2; k < n; k++) {
            double[] a = line.get(k - 1), b = line.get(k);
            for (int j = 0; j + 1 < k - 1; j++) {
                double[] c = line.get(j), d = line.get(j + 1);
                double d1 = side(c, d, a), d2 = side(c, d, b), d3 = side(a, b, c), d4 = side(a, b, d);
                if ((d1 > 0) == (d2 > 0) || (d3 > 0) == (d4 > 0)) continue;
                double crossing = arc[k - 1] + (arc[k] - arc[k - 1]) * d1 / (d1 - d2), end = crossing + keep * (arc[n - 1] - crossing);
                int e = k;
                while (e < n - 1 && arc[e] < end) e++;
                double f = (end - arc[e - 1]) / (arc[e] - arc[e - 1]);
                double[] p = line.get(e - 1), q = line.get(e);
                List<double[]> kept = new ArrayList<>(line.subList(0, e));
                kept.add(new double[]{p[0] + (q[0] - p[0]) * f, p[1] + (q[1] - p[1]) * f});
                return kept;
            }
        }
        return line;
    }
    /** The loop's pressure {@code u} of the way along: light at the start, firm from {@link #LOOP_FIRM} of the way to the end. */
    static double firmEnd(double u) { return .15 + .75 * Math.pow(Math.sin(Math.PI / 2 * Math.min(1, u / LOOP_FIRM)), .7); }
    /** Which side of {@code c}–{@code d} point {@code a} is on, times the line's length. */
    private static double side(double[] c, double[] d, double[] a) { return (d[0] - c[0]) * (a[1] - c[1]) - (d[1] - c[1]) * (a[0] - c[0]); }
    /**
     * How the pen presses along a wave: light to firm to light and slower where firm (ui.js wavePath); rising from
     * light to firm all the way and slowing less as it goes (Control's ramp); or as the loop does, light at the start
     * and firm from {@link #LOOP_FIRM} of the way to the end, slowing as it firms, so it lifts firm and wet (Drying's,
     * so what happens at and after lift has paint to act on).
     */
    enum Press { SWELL, RAMP, LIFT }
    /**
     * ui.js wavePath (483–494): a wave across {@code w × h} from {@code pad} to {@code pad}, {@code amp} either side of
     * the middle, {@code waves} periods, at least 30 points about every 2.5 px, pressed as {@code press} says. {@code
     * tilt}: the pen leans from upright to 55 degrees, its lean turning from 200 to 320 degrees on the page; else
     * upright. Each point is {x, y, pressure travel 0–1, time in ms, tilt X, tilt Y}, tilts in the pen's signed
     * degrees, as {@link BrushDirection} reads them.
     */
    static float[][] wave(int w, int h, float pad, float amp, double waves, Press press, boolean tilt) {
        int n = Math.max(30, (int)Math.ceil((w - 2 * pad) / 2.5));
        float[][] points = new float[n + 1][];
        double time = 0;
        for (int i = 0; i <= n; i++) {
            double u = (double)i / n, env = Math.sin(Math.PI * u), pressure;
            switch (press) {
                case RAMP: pressure = .08 + .92 * Math.pow(u, 1.2); time += 2 + 5 * (1 - u); break;
                case LIFT: pressure = firmEnd(u); time += 2 + 3 * pressure; break;
                default: pressure = .15 + .78 * Math.pow(env, .7); time += 2 + 4 * (1 - env);
            }
            double lean = Math.tan(Math.toRadians(u * 55)), turn = Math.toRadians(200 + u * 120);
            points[i] = new float[]{(float)(pad + (w - 2 * pad) * u), (float)(h / 2.0 + amp * Math.sin(2 * Math.PI * waves * u)), (float)pressure, (float)time,
                    tilt ? (float)Math.toDegrees(Math.atan(lean * Math.cos(turn))) : 0, tilt ? (float)Math.toDegrees(Math.atan(lean * Math.sin(turn))) : 0};
        }
        return points;
    }
    /**
     * The bar's tray stroke (the owner's "B Pen tray", 2026-10-19): one short gentle stroke lying across {@code width ×
     * height}, its centre line {@code amp} either side of the middle along the mockup's curve (a cubic through .62, .3,
     * .8 and .42 of the height: a little below the middle, a shallow dip, up at the end). As the drawn tools do (the
     * owner, 2026-10-27: "the stroke emerges from the left (as if it started off screen) and continue to where the brush
     * images end"): firm from the row's left edge, its mark, {@code size} px wide, ending at {@link DrawLayout#TIP_END}
     * with its own lift. The pen goes down {@link #trayLead} px left of the row, level with the curve's start, and
     * presses in there, light to firm (.1 + .85 smoothstep), so no taper in shows. 6 − 3 (p − .1) / .85 ms a step, so
     * slower where firm. 61 points of {x, y, pressure travel 0–1, time in ms} on the row, after the lead's.
     */
    static float[][] tray(float width, float height, float size, float amp) {
        float lead = trayLead(size), end = DrawLayout.TIP_END * width - size / 2;
        int in = (int) Math.ceil(60 * lead / end);
        float[][] points = new float[in + 61][];
        double time = 0;
        for (int i = 0; i < points.length; i++) {
            double s = Math.min(1, (double) i / in), t = Math.max(0, (i - in) / 60.0), u = 1 - t, p = .1 + .85 * s * s * (3 - 2 * s);
            double curve = .62 * u * u * u + .9 * u * u * t + 2.4 * u * t * t + .42 * t * t * t;
            if (i > 0) time += 6 - 3 * (p - .1) / .85;
            points[i] = new float[]{(float)(i < in ? lead * (i - in) / in : t * end), (float)(height / 2.0 + amp * (curve - .52) / .1), (float)p, (float)time};
        }
        return points;
    }
    /**
     * A tray stroke's room in a {@code width × height} row at {@code size} px: its lead off the row's left edge, the
     * longest taper in a mark that size has ({@link StrokeMath#taperLens}) and half its width; its curve as deep as the
     * mockup's (a tenth of the height either side), shallower for a broad stroke so it stays in the row, flat at the
     * most a row holds ({@link #trayCap}).
     */
    static float trayLead(float size) { return (float) StrokeMath.taperLens(1, 0, size)[0] + size / 2; }
    static float trayAmp(float size, int height) { return Math.max(0, Math.min(height * .1f, (height - size) / 2f - 1)); }
    /** The broadest a tray stroke draws in a row {@code height} px tall: .6 of it, so its curve and taper still show. */
    static float trayCap(int height) { return height * .6f; }
    /**
     * A tray stroke a very faint brush draws (a light pencil, a pale grey) is hard to see on the bar (the owner,
     * 2026-10-19). Samples are the page's dots, pure black on paper, so a stroke's "tone" is how close its dots lie, and
     * a stroke is faint when it is both thin and sparse: its densest patch ({@link #densest}) under {@link #LEGIBLE} and
     * all its ink under {@link #LEGIBLE_INK} of the row ({@link #ink}). Such a stroke is drawn again a little darker,
     * its densest patch about {@link #LEGIBLE_AT}, the same brush, size and texture. A broad pale wash has ink enough to
     * read and is as drawn (its whole area at its own grey reads; darkened, a Lighter wash would look Dark). On the bar
     * only: the brush and its grey never change, and the grey face shows the real grey.
     */
    static final double LEGIBLE = .45, LEGIBLE_INK = .025, LEGIBLE_AT = .35;
    /** The darkness every brush's tray stroke is drawn at, the library cards' too: full, black for ink (a wash its black's). */
    static final double PREVIEW = 1;
    /**
     * A slot's (or the smudge tool's) tray stroke, {@code width × height}: the drawing screen's bar's row ({@link
     * SlotTile#row}), the brush line's, the Tools page's and Brush Lab's slots and This Brush. At {@code look}'s px, no
     * broader than {@link #trayCap}, and at {@link #PREVIEW} whatever {@code look}'s grey (the one grey, 2026-10-24: a
     * brush's picture is the brush, not the grey in use; a White brush light on its dark ground, {@link #darkGround}),
     * legible ({@link #legibleTray}). Kept, as samples are.
     */
    static int[] slotTray(ModularBrush b, ToolSwatches.Look look, int width, int height) {
        return legibleTray(b, PREVIEW, (float) Math.min(look.px, trayCap(height)), width, height, true);
    }
    /**
     * Whether {@code b}'s pictures are drawn on a dark ground, its light stroke on it: a brush that lays white (Blend's
     * White: the White gel pen, a white or lightening blend), which on paper would lay nothing (newmods.js
     * renderSample). Every other brush's are on paper (the owner: grey grounds ghost on e-ink).
     */
    static boolean darkGround(ModularBrush b) { return ResolvedBrush.of(b, 8, 1).blendMode == ResolvedBrush.BlendMode.WHITE; }
    /** {@link #modular(ModularBrush, double, Shape, float, int, int, boolean)}'s tray stroke, legible ({@link #LEGIBLE}); kept as samples are. */
    static int[] legibleTray(ModularBrush b, double darkness, float size, int width, int height, boolean keep) {
        int[] drawn = modular(b, darkness, Shape.TRAY, size, width, height, keep);
        double d = darkness;
        // Dots don't darken in proportion, so twice at most.
        for (int pass = 0; pass < 2 && d < 1; pass++) {
            double densest = densest(drawn, width, height);
            if (densest >= LEGIBLE || densest <= 0 || pass == 0 && ink(drawn) >= LEGIBLE_INK || densest >= LEGIBLE_AT) break;
            d = Math.min(1, d * LEGIBLE_AT / densest);
            drawn = modular(b, d, Shape.TRAY, size, width, height, keep);
        }
        return drawn;
    }
    /** How much ink {@code argb} (greys) holds, 0 (paper) to 1 (all black). */
    static double ink(int[] argb) {
        long ink = 0;
        for (int p : argb) ink += 255 - (p & 0xff);
        return ink / (255.0 * Math.max(1, argb.length));
    }
    /** How dark the darkest patch of {@code argb} ({@code width × height} greys) is, 0–1: its squares a fifth of the height (4 px at least) on average. */
    static double densest(int[] argb, int width, int height) {
        int block = Math.max(4, height / 5);
        double best = 0;
        for (int y0 = 0; y0 + block <= height; y0 += block / 2)
            for (int x0 = 0; x0 + block <= width; x0 += block / 2) {
                long ink = 0;
                for (int y = y0; y < y0 + block; y++) for (int x = x0; x < x0 + block; x++) ink += 255 - (argb[y * width + x] & 0xff);
                best = Math.max(best, ink / (255.0 * block * block));
            }
        return best;
    }
    /** Whether a slot's tile, its loop no broader than {@code cap} px, is narrower than the slot draws at {@code look}: it then says the size in mm. */
    static boolean shrunk(ToolSwatches.Look look, float cap) { return look.px > cap; }
    /**
     * A six-part brush's sample at {@code darkness} 0–1 in {@code shape} across {@code width × height}, at the size that
     * fits the tile ({@link ModularSizes#sampleSize}, of the loop's reach for a loop, the height for a tray stroke), whatever
     * the preset; kept by its settings, the darkness, the shape, the size and the tile, unless not {@code keep}. A
     * Drying brush shows settled at lift.
     */
    static int[] modular(ModularBrush b, double darkness, Shape shape, int width, int height, boolean keep) {
        return modular(b, darkness, shape, ModularSizes.sampleSize(b, shape == Shape.LOOP ? reach(width, height) : height), width, height, keep);
    }
    /** As above at {@code size} px: a slot's tile or tray stroke at the slot's own size and grey. */
    static int[] modular(ModularBrush b, double darkness, Shape shape, float size, int width, int height, boolean keep) {
        float ref = shape == Shape.LOOP ? reach(width, height) : height;
        // A smudge that lays little paint of its own (a Stump) shows over a grey band on its own layer, for it to smear.
        boolean smears = smearsMostly(b);
        // A Spiro in Ring sizes its ring in a stroke of its own (DrawingPad.RingSetup): its loop is a tap, the whole rosette mid-tile.
        Supplier<float[][]> points = shape == Shape.LOOP && SpiroStamper.ring(ResolvedBrush.of(b, size, darkness).pattern)
                ? () -> new float[][]{{width / 2f, height / 2f, (float) TYPICAL_PRESS, 0}}
                : () -> typical(points(shape, width, height, size, ModularSizes.samplePad(size, ref)));
        return modular(b, darkness, shape + " " + size, size, points, width, height, keep,
                page -> { if (smears) band(page, .42, .58, 150, ownLayer(b)); }, ModularWords.clearWater(b) ? ref : 0, true);
    }
    /**
     * The pressure travel a hand usually draws at (match-lab's "usual pressure of 0.6"): every brush's loop and tray
     * stroke, and a part card's wave but Hand's ramp and Drying's firm lift, are pressed no firmer, so a sample shows
     * the tone a stroke usually lays, not its darkest. First for the prints whose shade follows pressure (the owner,
     * 2026-10-22: "preview for the multi-shade print patterns can be less dark to match the actual typical output"),
     * then for all (the same day: "the preview marks are much darker than the typical line left at normal pressure",
     * "ie. crayon"): pencils, charcoal, crayon, pastels and washes were 1.2 to 2.5 times as dark as a stroke at .6.
     */
    static final double TYPICAL_PRESS = .6;
    /** {@code points} pressed no firmer than {@link #TYPICAL_PRESS}. */
    static float[][] typical(float[][] points) {
        for (float[] p : points) p[2] = (float) Math.min(p[2], TYPICAL_PRESS);
        return points;
    }
    /** Whether {@code b} smudges and lays under half its own paint: on blank paper it would show little or nothing. */
    static boolean smearsMostly(ModularBrush b) { ResolvedBrush r = ResolvedBrush.of(b, 8, 1); return r.carry != null && r.carry.moves && r.load < .5; }
    /** The layer {@code b} draws on: ink for ink, charcoal and graphite, wash for wash and cover ({@link ModularStroke#selectLayer}). */
    private static int ownLayer(ModularBrush b) { return ModularSizes.inks(b) ? InkWashLayers.INK : InkWashLayers.WASH; }

    /**
     * Part {@code role}'s card sample of six-part brush {@code b} across {@code width × height} at Dark (a wash at the old
     * black, {@link ModularSizes#sampleDarkness}) (ui.js
     * renderSample, 510–546): what that part adds, always a wave (a part, not a whole brush: DESIGN.md "Loop and
     * wave"). Tip: a wave; Medium: the Tip's wave over a grey band, so cover and transparency show where it crosses;
     * Drying: a wave that lifts firm ({@link Press#LIFT}) over the band, so the settled body, the re-tapered tail and
     * wet edges show; Carry: a slow wave over a dark band and a darker strip on the brush's own layer, for it to pick
     * up and drag; Paper: a flatter wave, broader ({@link ModularSizes#sampleSize}), so the grain reads; Hand: a
     * pressure ramp with the pen leaning further as it goes. Kept by {@link #key}, the role and the tile.
     */
    static int[] part(ModularBrush b, BrushPart role, int width, int height) {
        float ref = waveReach(width, height), size = ModularSizes.sampleSize(b, ref, role);
        float pad = (float)Math.max(size * (size < ref / 7.0 ? 2 : .7) + 6, width * .06), amp = (float)Math.max(3, Math.min(ref * .3, height / 2.0 - size * .6 - 6));
        Supplier<float[][]> points;
        // Filled Shape's card: a blob, closed and filled; every other card's wave is the outline alone.
        boolean shape = role == BrushPart.CARRY && FilledShape.fills(b);
        if (shape) points = () -> typical(blob(width, height, pad));
        else switch (role) {
            case CARRY: points = () -> typical(wave(width, height, pad, amp * .4f, .75, Press.SWELL, false)); break;
            case PAPER: points = () -> typical(wave(width, height, pad, amp * .35f, .75, Press.SWELL, false)); break;
            case HAND: points = () -> wave(width, height, pad, amp * .6f, 1, Press.RAMP, true); break;
            case LIFE: points = () -> wave(width, height, pad, amp * .7f, 1, Press.LIFT, false); break;
            default: points = () -> typical(wave(width, height, pad, amp, 1.25, Press.SWELL, false));
        }
        boolean rings = b.choice(BrushPart.LIFE) == PartChoice.FLOAT && (role == BrushPart.LIFE || ModularWords.clearWater(b));
        return modular(b, ModularSizes.sampleDarkness(b), role.toString(), size, points, width, height, true, page -> {
            if (role == BrushPart.MEDIUM || role == BrushPart.LIFE) band(page, .42, .58, 150, InkWashLayers.WASH);
            // A smudge moves only its own layer: the band and strip are on it.
            if (role == BrushPart.CARRY && !shape) { band(page, .42, .58, 60, ownLayer(b)); band(page, .2, .24, 40, ownLayer(b)); }
        }, rings ? ref : 0, shape);
    }
    /**
     * How far a loop reaches in a {@code width × height} tile, the size its mark is measured against: its width, or .73
     * of a wide tile's height: about its width as drawn, the loop standing a little taller than wide (159 × 186; .73 is
     * .85 of 159 / 186, as a wave's .62 in {@link #waveReach}).
     */
    static float reach(int width, int height) { return (float)Math.min(width, height * .73); }
    /** How far a part card's wave reaches in a {@code width × height} tile: its height, or .62 of a narrow tile's width. */
    static float waveReach(int width, int height) { return (float)Math.min(height, width * .62); }
    /**
     * {@code b}'s stroke at {@code size} and {@code darkness} along {@code points}, over what {@code under} lays on the
     * blank page first; kept by its settings, the darkness, {@code what} it shows and the tile, if {@code keep}. A Filled
     * Shape's closed and filled if {@code shapes} ({@link FilledShape}).
     */
    private static int[] modular(ModularBrush b, double darkness, String what, float size, Supplier<float[][]> points, int width, int height,
                                 boolean keep, Consumer<ToneDocument> under, float rings, boolean shapes) {
        String key = "parts " + b.key() + " " + String.format(Locale.ROOT, "%.3f", darkness);
        boolean dark = darkGround(b);
        // A resist shows by what refuses to take: a wash laid over it, every role (no band under it).
        boolean resist = b.choice(BrushPart.MEDIUM) == PartChoice.RESIST;
        // A crayon's scratch shows by what it scrapes away: a waxy ground of the same crayon, Scratch off, laid first.
        boolean scratch = ModularWords.scratch(b);
        return keep(key + " " + what + " " + width + "x" + height, points, width, height, keep, page -> {
            if (scratch) waxGround(page, b);
            else if (dark) band(page, 0, 1, DARK_GROUND, InkWashLayers.WASH);
            if (resist) return washedOver(page, shaped(page, b, size, darkness, shapes));
            under.accept(page);
            return rings > 0 ? new FloatSample(page, b, size, darkness, rings) : shaped(page, b, size, darkness, shapes);
        });
    }
    /** {@code b}'s stroke on {@code page}; a Filled Shape's filled at its finish if {@code shapes}. */
    private static DrawingStroke shaped(ToneDocument page, ModularBrush b, float size, double darkness, boolean shapes) {
        DrawingStroke s = ModularStroke.start(page, b, size, darkness, null);
        return shapes ? FilledShape.of(page, b, s, size, darkness, null) : s;
    }
    /**
     * Filled Shape's card sample: a lopsided round blob across {@code w × h}, {@code pad} from the sides, drawn round once
     * from the top, its end short of its start so the closing line shows; pressed as a wave swells, about every 2 px.
     */
    static float[][] blob(int w, int h, float pad) {
        double cx = w / 2.0, cy = h / 2.0, rx = Math.max(4, (w / 2.0 - pad) / 1.17), ry = Math.max(4, (h / 2.0 - pad) / 1.17);
        int n = Math.max(40, (int) Math.ceil(Math.PI * (rx + ry) / 2));
        float[][] p = new float[n + 1][];
        for (int i = 0; i <= n; i++) {
            double u = .93 * i / n, a = -Math.PI / 2 + 2 * Math.PI * u, wob = 1 + .12 * Math.sin(3 * a + .7) + .05 * Math.sin(5 * a);
            p[i] = new float[]{(float) (cx + rx * wob * Math.cos(a)), (float) (cy + ry * wob * Math.sin(a)), (float) (.3 + .6 * Math.sin(Math.PI * u / .93)), 4 * i};
        }
        return p;
    }
    /** The wash a resist's samples lay over it once drawn (newmods.js RESIST_GROUND): the grey band's grey. */
    static final int RESIST_GROUND = 150;
    /** {@code stroke} (a resist's), and when it finishes a wash of {@link #RESIST_GROUND} over the whole sample, which takes only where it can. */
    private static DrawingStroke washedOver(ToneDocument page, DrawingStroke stroke) {
        return new DrawingStroke() {
            @Override public void sample(float x, float y, float pressure, float tiltX, float tiltY) { stroke.sample(x, y, pressure, tiltX, tiltY); }
            @Override public void sample(float x, float y, float pressure, float tiltX, float tiltY, long time) { stroke.sample(x, y, pressure, tiltX, tiltY, time); }
            @Override public boolean finish() { boolean changed = stroke.finish(); band(page, 0, 1, RESIST_GROUND, InkWashLayers.WASH); return changed; }
        };
    }
    /**
     * A Float brush's rings (ui.js floatSample): its stroke floating, then drops of it and of clear water in turn at the
     * middle, held a little longer each time, then printed. Clear water lays no stroke and drops its paint's twin (Smudge
     * off) in turn with itself; a clear-water comb (a Rake or Split nib) drops them with a round tip, then combs across
     * them. Drawn as one stroke for {@link #draw}.
     */
    private static final class FloatSample implements DrawingStroke {
        private final ToneDocument page;
        private final ModularBrush own, clear, comb;
        private final WatercolorSession session = new WatercolorSession();
        private final DrawingStroke stroke;
        private final double size, darkness;
        FloatSample(ToneDocument page, ModularBrush b, float size, double darkness, float ref) {
            this.page = page; this.darkness = darkness;
            boolean cw = ModularWords.clearWater(b);
            comb = cw && ModularWords.combs(b) ? b : null;
            own = cw ? b.copy() : b;
            if (cw) own.set(BrushPart.CARRY, new ModularBrush.Part(PartChoice.OFF));
            clear = b.copy();
            clear.set(BrushPart.CARRY, ModularBrush.switchTo(b.part(BrushPart.CARRY), PartChoice.SMUDGE));
            clear.part(BrushPart.CARRY).set(PartDial.LOAD, 0);
            if (comb != null) for (ModularBrush t : new ModularBrush[]{own, clear}) t.set(BrushPart.TIP, new ModularBrush.Part(PartChoice.ROUND));
            this.size = Math.max(2, Math.min(size, ref / 9));
            stroke = cw ? null : ModularStroke.start(page, b, size, darkness, session);
        }
        @Override public void sample(float x, float y, float pressure, float tiltX, float tiltY) { sample(x, y, pressure, tiltX, tiltY, PenSpeed.UNTIMED); }
        @Override public void sample(float x, float y, float pressure, float tiltX, float tiltY, long time) { if (stroke != null) stroke.sample(x, y, pressure, tiltX, tiltY, time); }
        @Override public boolean finish() {
            if (stroke != null) stroke.finish();
            float cx = page.width / 2f, cy = page.height / 2f;
            for (int i = 0; i < 5; i++) {
                tap(own, i == 2 ? darkness * .5 : darkness, cx, cy, 260 + 90 * i, 1000 * i);
                tap(clear, darkness, cx, cy, 160, 1000 * i + 500);
            }
            if (comb != null) {
                session.settleBefore(page, comb);
                DrawingStroke s = ModularStroke.start(page, comb, size, darkness, session);
                for (int i = 0; i <= 80; i++) s.sample(page.width * (.05f + .9f * i / 80), cy, ToolSettings.pressure(.7f), Float.NaN, Float.NaN, 6000 + 8 * i);
                s.finish();
            }
            session.settleNow(page);
            return true;
        }
        private void tap(ModularBrush b, double dark, float x, float y, int hold, long t0) {
            session.settleBefore(page, b);
            DrawingStroke s = ModularStroke.start(page, b, size, dark, session);
            for (int t = 0; t <= hold; t += 50) s.sample(x, y, ToolSettings.pressure(.8f), Float.NaN, Float.NaN, t0 + t);
            s.finish();
        }
    }
    /**
     * The ground a crayon's scratch (Scratch on) samples scrape back to white, as {@code PresetPictures} lays one: the
     * same crayon with Scratch off, rubbed firmly across the whole blank page in overlapping rows, at {@link
     * #WAX_GROUND} darkness, so its tooth shows around the scratched line.
     */
    private static void waxGround(ToneDocument page, ModularBrush b) {
        ModularBrush wax = b.copy();
        wax.part(BrushPart.MEDIUM).set(PartDial.SCRATCH, 0);
        int rows = 4;
        double size = 1.6 * page.height / rows;
        for (int k = 0; k < rows; k++) {
            DrawingStroke s = ModularStroke.start(page, wax, size, WAX_GROUND, null);
            double y = page.height * (k + .5) / rows;
            for (int i = 0; i <= 40; i++)
                s.sample((float)(page.width * (-.1 + 1.2 * i / 40)), (float)(y + size * .08 * Math.sin(i * .7)), ToolSettings.pressure(.9f), Float.NaN, Float.NaN, 6L * i);
            s.finish();
        }
    }
    /** How dark the waxy ground under a crayon's scratch is laid: PresetPictures' .8. */
    static final double WAX_GROUND = .8;
    /** The dark ground a White brush's samples draw on (newmods.js sampleSurf's 68). */
    static final int DARK_GROUND = 68;
    /** A band of {@code tone} down the blank page's {@code layer}, from {@code from} to {@code to} of its width (ui.js 518–523). */
    private static void band(ToneDocument page, double from, double to, int tone, int layer) {
        int x0 = (int)Math.round(page.width * from), w = (int)Math.round(page.width * to) - x0;
        int[] grays = new int[w * page.height];
        Arrays.fill(grays, tone);
        page.selectLayer(layer);
        page.begin(); page.setTones(x0, 0, w, page.height, grays); page.finish();
    }

    /** {@code shape}'s points across {@code width × height} for a mark {@code size} px wide: a loop {@code loopPad} from the sides, or a tray stroke. */
    private static float[][] points(Shape shape, int width, int height, float size, float loopPad) {
        return shape == Shape.LOOP ? loop(width, height, loopPad, 0, 0) : tray(width, height, size, trayAmp(size, height));
    }
    /** The pixels kept under {@code full}, or the {@code points} drawn by {@code maker}'s stroke, then kept if {@code keep}. */
    private static int[] keep(String full, Supplier<float[][]> points, int width, int height, boolean keep, Maker maker) {
        synchronized (kept) {
            int[] pixels = kept.get(full);
            if (pixels != null) return pixels;
        }
        int[] pixels = draw(points.get(), width, height, maker);
        if (!keep) return pixels;
        synchronized (kept) {
            int[] replaced = kept.put(full, pixels);
            keptPixels += pixels.length - (replaced != null ? replaced.length : 0);
            // The least recently asked for go first; the one just drawn stays.
            Iterator<int[]> eldest = kept.values().iterator();
            while ((kept.size() > KEPT || keptPixels > KEPT_PIXELS) && kept.size() > 1) { keptPixels -= eldest.next().length; eldest.remove(); }
        }
        return pixels;
    }

    private static int[] draw(float[][] points, int width, int height, Maker maker) {
        ToneDocument page = InkWashLayers.blank(width, height);
        DrawingStroke stroke = maker.start(page);
        // Points without a tilt have no reading: tilt changes nothing, as for a pen held upright, and a wide Tilt range
        // draws its natural hold rather than its upright fine point.
        for (float[] point : points)
            stroke.sample(point[0], point[1], ToolSettings.pressure(point[2]), point.length > 4 ? point[4] : Float.NaN, point.length > 5 ? point[5] : Float.NaN, Math.round(point[3]));
        stroke.finish();
        int[] pixels = new int[width * height];
        page.render(pixels, 0, 0, width, height);
        return pixels;
    }
}
