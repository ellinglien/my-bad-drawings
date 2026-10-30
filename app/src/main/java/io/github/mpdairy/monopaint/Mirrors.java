package io.github.mpdairy.monopaint;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A page's mirrors (the owner's, 2026-10-19: "mirrors is really cool, i love it. perfect, no notes"; Mischief Lab 2,
 * section 6): up to {@link #MOST} straight lines on the page, never in its layers, and the group of maps they make
 * ({@link Group}): each mirror's reflection, reflections of reflections and so on, found breadth first, at most
 * {@link #MOST_COPIES} counting the page itself. Two or more through one point make a kaleidoscope.
 *
 * <p>Two styles ({@link Style}). <b>Copy Strokes</b>: every stroke is drawn again for each map ({@link MirrorStroke}).
 * <b>Real Mirror</b> (the owner, 2026-10-19: "like a real mirror placed on the page"): the cell of the group that
 * holds the kept point ({@link #keepX}, {@link #keepY}) shows as it is, and every other cell shows its reflection of
 * it ({@link View}): only the view reflects, the page's layers keep what they hold, until Keep Reflection lays it
 * into them ({@link ToneDocument#keepReflection}). The pen draws on any side and through the mirrors (the owner,
 * 2026-10-19: "allow drawing on either side after placement (and allow drawing 'through' the mirror"): each sample
 * folds into the kept cell, so its marks are the kept side's and show on every side ({@link MirrorStroke}). The side
 * kept is at first the one with most marks ({@link #mostInk}), then Flip Side's ({@link #flipped}).
 *
 * <p>Immutable: every change makes another, so an Undo step holds them before and after by reference ({@link
 * ToneDocument#setMirrors}) and a file keeps them in TSM5's MIRR section ({@link DocumentCodec}, {@link #encode}),
 * written only for a page with mirrors. Lengths are the web prototype's, its CSS pixels scaled to page pixels ({@link
 * #PX}). A fill is mirrored as a stroke is ({@code FillTool.Areas}): with Copy Strokes the tap's area and its images'
 * ({@link Group#points}), with Real Mirror the tap folded into the kept side. Pure, checked on the desktop ({@code
 * MirrorChecks}).
 */
final class Mirrors {
    /** Copy Strokes: strokes drawn again in the reflections. Real Mirror: the kept side shown in the others, as a mirror stood on the paper. */
    enum Style { COPY, REAL }
    /** A mirror: a line through {@code x, y} (page pixels) at {@code angle} radians, {@code dx, dy} its unit direction. */
    static final class Line {
        final double x, y, angle, dx, dy;
        Line(double x, double y, double angle) { this.x = x; this.y = y; this.angle = angle; dx = Math.cos(angle); dy = Math.sin(angle); }
        /** How far {@code px, py} is from the line, signed by its side. */
        double side(double px, double py) { return (px - x) * -dy + (py - y) * dx; }
        @Override public boolean equals(Object o) { return o instanceof Line && ((Line) o).x == x && ((Line) o).y == y && ((Line) o).angle == angle; }
        @Override public int hashCode() { return Double.hashCode(x) * 961 + Double.hashCode(y) * 31 + Double.hashCode(angle); }
    }

    /** At most this many mirrors; a fifth replaces the oldest. */
    static final int MOST = 4;
    /** At most this many maps, the page itself counted: "copies max" past it (non-dividing angles, parallel mirrors). */
    static final int MOST_COPIES = 24;
    /** Page pixels a prototype pixel (a CSS pixel, 1/96 inch) is on the 300 ppi panel. */
    static final double PX = 300 / 96.0;
    /** A placing stroke shorter than this along its line is refused ("Too short"): 24 prototype pixels, about 6 mm. */
    static final double SHORT = 24 * PX;
    /** A mirror's angle snaps to 15° steps when within about 3° (.055 rad) of one. */
    static final double SNAP_STEP = Math.PI / 12, SNAP_WITHIN = .055;
    /** A mirror moves to pass through a crossing of mirrors, or the page's centre, when it passes within 12 prototype pixels of one. */
    static final double SPOT = 12 * PX;
    /** The guides: a line .75 prototype pixels either side of the mirror, dashes 5 on, 5 off; dark grey (the prototype's 11 of 15). */
    static final double HALF_WIDTH = .75 * PX, DASH = 5 * PX;
    static final int GUIDE_TONE = 255 - 17 * 11;
    /** A placing stroke shows light grey while drawn (the prototype's 6 of 15), never in the drawing. */
    static final int PLACING_TONE = 255 - 17 * 6;
    /** Kaleidoscope: three mirrors through the page's centre at these angles, degrees (six copies). */
    static final int[] KALEIDOSCOPE = {90, 30, 150};

    static final Mirrors NONE = new Mirrors(Collections.<Line>emptyList(), true, true, Style.COPY, Double.NaN, Double.NaN);

    /** The mirrors, oldest first. */
    final List<Line> lines;
    /** Whether they act (strokes copied, or the reflection shown) and their guides may show ({@link #guides}): the Mirrors row. Not part of Undo. */
    final boolean on;
    /** Whether their dashed guides show (the Show Guides row; on at first); off, the mirrors act all the same. Not part of Undo. */
    final boolean guides;
    final Style style;
    /** Real Mirror: a point in the cell that is kept (page pixels); NaN until chosen. */
    final double keepX, keepY;
    private Group group;
    private View view;

    private Mirrors(List<Line> lines, boolean on, boolean guides, Style style, double keepX, double keepY) {
        this.lines = Collections.unmodifiableList(lines); this.on = on; this.guides = guides; this.style = style; this.keepX = keepX; this.keepY = keepY;
    }

    boolean isEmpty() { return lines.isEmpty(); }
    int size() { return lines.size(); }
    /** Whether they act: on, with at least one mirror. */
    boolean acts() { return on && !isEmpty(); }
    /** Whether the canvas draws their guides: they act, and Show Guides is on. */
    boolean guidesShown() { return acts() && guides; }
    /** Whether strokes are drawn again in the reflections (Copy Strokes, on). */
    boolean copies() { return acts() && style == Style.COPY && group().maps.length > 1; }
    /** Whether the page shows the kept cell's reflection in the others (Real Mirror, on, a side kept). */
    boolean reflects() { return acts() && style == Style.REAL && !Double.isNaN(keepX) && group().maps.length > 1; }

    /** With {@code line} added, the oldest making way past {@link #MOST}; on. */
    Mirrors add(Line line) {
        ArrayList<Line> next = new ArrayList<>(lines);
        next.add(line);
        while (next.size() > MOST) next.remove(0);
        return new Mirrors(next, true, guides, style, keepX, keepY);
    }
    /** On or off. */
    Mirrors turned(boolean on) { return on == this.on ? this : new Mirrors(new ArrayList<>(lines), on, guides, style, keepX, keepY); }
    /** Their guides shown or not. */
    Mirrors guided(boolean guides) { return guides == this.guides ? this : new Mirrors(new ArrayList<>(lines), on, guides, style, keepX, keepY); }
    Mirrors styled(Style style) { return style == this.style ? this : new Mirrors(new ArrayList<>(lines), on, guides, style, keepX, keepY); }
    /** Keeping the cell that holds {@code x, y} (Real Mirror). */
    Mirrors keeping(double x, double y) { return new Mirrors(new ArrayList<>(lines), on, guides, style, x, y); }
    /**
     * Real Mirror's Flip Side: keeping the next cell round, of the cells whose image of the kept point lies on the {@code
     * width × height} page (all of them if fewer than two do), taken in turn round their middle; one mirror's other
     * side. Every image is one of the same set, so flipping again goes on round and comes back.
     */
    Mirrors flipped(int width, int height) {
        View v = view();
        if (v == null) return this;
        List<double[]> on = new ArrayList<>();
        for (int k = 0; k < v.ox.length; k++)
            if (k == 0 || v.ox[k] >= 0 && v.oy[k] >= 0 && v.ox[k] < width && v.oy[k] < height) on.add(new double[]{v.ox[k], v.oy[k], k});
        if (on.size() < 2) { on.clear(); for (int k = 0; k < v.ox.length; k++) on.add(new double[]{v.ox[k], v.oy[k], k}); }
        double cx = 0, cy = 0;
        for (double[] p : on) { cx += p[0]; cy += p[1]; }
        double mx = cx / on.size(), my = cy / on.size();
        on.sort((a, b) -> {
            int c = Double.compare(Math.atan2(a[1] - my, a[0] - mx), Math.atan2(b[1] - my, b[0] - mx));
            return c != 0 ? c : Double.compare(Math.hypot(a[0] - mx, a[1] - my), Math.hypot(b[0] - mx, b[1] - my));
        });
        int at = 0;
        while (on.get(at)[2] != 0) at++;
        double[] next = on.get((at + 1) % on.size());
        return keeping(next[0], next[1]);
    }
    /** No mirrors (Remove Mirrors), the style and the guides' setting kept. */
    Mirrors cleared() { return style == Style.COPY && on && guides ? NONE : new Mirrors(new ArrayList<Line>(), on, guides, style, Double.NaN, Double.NaN); }
    /** Three mirrors through the centre of a {@code width × height} page at 90°, 30° and 150°: six copies; on, the style kept. */
    Mirrors kaleidoscope(int width, int height) {
        ArrayList<Line> next = new ArrayList<>();
        for (int deg : KALEIDOSCOPE) next.add(new Line(width / 2.0, height / 2.0, Math.toRadians(deg)));
        return new Mirrors(next, true, guides, style, keepX, keepY);
    }

    /** The group the mirrors make, worked out once. */
    Group group() {
        if (group == null) group = new Group(lines);
        return group;
    }
    /** Real Mirror's view ({@link #reflects}), worked out once; null when it reflects nothing. */
    View view() {
        if (!reflects()) return null;
        if (view == null) view = new View(group(), keepX, keepY);
        return view;
    }

    /** "3 mirrors, 6 copies" (Copy Strokes), "3 mirrors, 5 reflections" (Real Mirror), "copies max" when the cap cut it off; "No mirrors". */
    String caption() {
        int n = size();
        if (n == 0) return "No mirrors";
        int maps = group().maps.length;
        String mirrors = n + (n == 1 ? " mirror, " : " mirrors, ");
        if (style == Style.REAL) return mirrors + (maps - 1) + (maps == 2 ? " reflection" : " reflections") + (group().capped ? " max" : "");
        return mirrors + maps + (group().capped ? " copies max" : " copies");
    }

    /**
     * The mirror a roughly straight stroke {@code xs, ys} ({@code n} points, page pixels) places on a {@code width ×
     * height} page with these mirrors on it: its principal axis, the angle snapped to {@link #SNAP_STEP} when within
     * {@link #SNAP_WITHIN}, moved through a crossing of these mirrors or the page's centre within {@link #SPOT}; null
     * when shorter than {@link #SHORT} along it.
     */
    Line fit(float[] xs, float[] ys, int n, int width, int height) {
        if (n < 2) return null;
        double mx = 0, my = 0;
        for (int i = 0; i < n; i++) { mx += xs[i]; my += ys[i]; }
        mx /= n; my /= n;
        double sxx = 0, syy = 0, sxy = 0;
        for (int i = 0; i < n; i++) { double dx = xs[i] - mx, dy = ys[i] - my; sxx += dx * dx; syy += dy * dy; sxy += dx * dy; }
        double a = .5 * Math.atan2(2 * sxy, sxx - syy);
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY, c = Math.cos(a), s = Math.sin(a);
        for (int i = 0; i < n; i++) { double t = (xs[i] - mx) * c + (ys[i] - my) * s; lo = Math.min(lo, t); hi = Math.max(hi, t); }
        if (hi - lo < SHORT) return null;
        double snap = Math.round(a / SNAP_STEP) * SNAP_STEP;
        if (Math.abs(a - snap) < SNAP_WITHIN) a = snap;
        double dx = Math.cos(a), dy = Math.sin(a);
        List<double[]> spots = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) for (int j = i + 1; j < lines.size(); j++) { double[] x = cross(lines.get(i), lines.get(j)); if (x != null) spots.add(x); }
        spots.add(new double[]{width / 2.0, height / 2.0});
        for (double[] p : spots) if (Math.abs((p[0] - mx) * -dy + (p[1] - my) * dx) < SPOT) { mx = p[0]; my = p[1]; break; }
        // An angle of −90° and one of 90° are the same mirror: kept in (−90°, 90°].
        if (a <= -Math.PI / 2 + 1e-9) a += Math.PI;
        return new Line(mx, my, a);
    }
    /** Where two mirrors cross, or null when they are parallel. */
    static double[] cross(Line m, Line n) {
        double c = m.dx * n.dy - m.dy * n.dx;
        if (Math.abs(c) < 1e-6) return null;
        double t = ((n.x - m.x) * n.dy - (n.y - m.y) * n.dx) / c;
        return new double[]{m.x + m.dx * t, m.y + m.dy * t};
    }

    /** Whether the guides (dashed, {@link #HALF_WIDTH} either side of each mirror) cover the pixel centred at {@code px, py}. */
    boolean guideAt(double px, double py) {
        for (Line m : lines) {
            double cx = px - m.x, cy = py - m.y;
            if (Math.abs(cx * -m.dy + cy * m.dx) < HALF_WIDTH && ((int) Math.floor((cx * m.dx + cy * m.dy) / DASH) & 1) == 0) return true;
        }
        return false;
    }
    /**
     * The pixels of row {@code y} whose centres the guide of {@code m} may cover (within {@link #HALF_WIDTH} of it), as
     * {from, to} (exclusive), clipped to {@code x0..x1}; null for none.
     */
    static int[] guideSpan(Line m, int y, int x0, int x1) {
        double cy = y + .5 - m.y;
        if (Math.abs(m.dy) < 1e-9) return Math.abs(cy * m.dx) < HALF_WIDTH ? new int[]{x0, x1} : null;
        // (cx · −dy + cy · dx) within ±HALF_WIDTH, cx = x + .5 − m.x.
        double a = (cy * m.dx - HALF_WIDTH) / m.dy, b = (cy * m.dx + HALF_WIDTH) / m.dy;
        int from = Math.max(x0, (int) Math.floor(m.x + Math.min(a, b) - .5)), to = Math.min(x1, (int) Math.ceil(m.x + Math.max(a, b) - .5) + 1);
        return from < to ? new int[]{from, to} : null;
    }
    /** Whether any guide passes within reach of the box {@code x0, y0} to {@code x1, y1} (exclusive). */
    boolean guidesNear(int x0, int y0, int x1, int y1) {
        double cx = (x0 + x1) / 2.0, cy = (y0 + y1) / 2.0, r = Math.hypot(x1 - x0, y1 - y0) / 2 + HALF_WIDTH + 1;
        for (Line m : lines) if (Math.abs(m.side(cx, cy)) < r) return true;
        return false;
    }

    /**
     * Real Mirror's default side: of the cells round a point near the page's centre, the one holding the most marks of
     * {@code page} (a tone a pixel, {@code width} across, read every 8 pixels; under 224 counts), as the point to keep.
     */
    static double[] mostInk(Mirrors m, Tones page, int width, int height) {
        Group g = m.group();
        double[] seed = seed(g, width, height);
        View v = new View(g, seed[0], seed[1]);
        int[] counts = new int[g.maps.length];
        for (int y = 4; y < height; y += 8) for (int x = 4; x < width; x += 8) if (page.tone(x, y) < 224) counts[v.cell(x + .5, y + .5)]++;
        int best = 0;
        for (int k = 1; k < counts.length; k++) if (counts[k] > counts[best]) best = k;
        return new double[]{v.ox[best], v.oy[best]};
    }
    /** The page's tone at a pixel. */
    interface Tones { int tone(int x, int y); }
    /** A point near the page's centre on no mirror of {@code g}: its images all apart. */
    private static double[] seed(Group g, int width, int height) {
        for (int k = 1; ; k++) {
            double x = width * (.5 + .0731 * k % .4), y = height * (.5 + .0419 * k % .4);
            double[] ox = new double[g.maps.length], oy = new double[g.maps.length];
            boolean apart = true;
            for (int i = 0; i < g.maps.length && apart; i++) {
                double[] p = Group.apply(g.maps[i], x, y); ox[i] = p[0]; oy[i] = p[1];
                for (int j = 0; j < i; j++) if (Math.hypot(ox[i] - ox[j], oy[i] - oy[j]) < 8) { apart = false; break; }
            }
            if (apart || k > 50) return new double[]{x, y};
        }
    }

    /**
     * The maps the mirrors make: affine maps {a, b, c, d, e, f} ({@code x' = a x + b y + e, y' = c x + d y + f}), the
     * page itself first, then each mirror's reflection, then reflections of those, breadth first, at most {@link
     * #MOST_COPIES}; {@link #capped} when that cut them off.
     */
    static final class Group {
        static final double[] IDENTITY = {1, 0, 0, 1, 0, 0};
        final double[][] maps;
        final boolean capped;
        Group(List<Line> lines) {
            List<double[]> found = new ArrayList<>();
            found.add(IDENTITY);
            List<double[]> mirrors = new ArrayList<>();
            for (Line m : lines) mirrors.add(reflection(m));
            boolean cut = false;
            search:
            for (int q = 0; q < found.size(); q++)
                for (double[] m : mirrors) {
                    double[] next = compose(m, found.get(q));
                    boolean known = false;
                    for (double[] o : found) if (same(o, next)) { known = true; break; }
                    if (known) continue;
                    if (found.size() >= MOST_COPIES) { cut = true; break search; }
                    found.add(next);
                }
            maps = found.toArray(new double[0][]);
            capped = cut;
        }
        /** The reflection in {@code m}. */
        static double[] reflection(Line m) {
            double a = 2 * m.dx * m.dx - 1, b = 2 * m.dx * m.dy, d = 2 * m.dy * m.dy - 1;
            return new double[]{a, b, b, d, m.x - a * m.x - b * m.y, m.y - b * m.x - d * m.y};
        }
        /** {@code m} after {@code t}. */
        static double[] compose(double[] m, double[] t) {
            return new double[]{m[0] * t[0] + m[1] * t[2], m[0] * t[1] + m[1] * t[3], m[2] * t[0] + m[3] * t[2], m[2] * t[1] + m[3] * t[3],
                    m[0] * t[4] + m[1] * t[5] + m[4], m[2] * t[4] + m[3] * t[5] + m[5]};
        }
        /** The same map, to a ten-thousandth in its turn and half a pixel in its move. */
        static boolean same(double[] a, double[] b) {
            for (int k = 0; k < 6; k++) if (Math.abs(a[k] - b[k]) >= (k > 3 ? .5 : 1e-4)) return false;
            return true;
        }
        static double[] apply(double[] t, double x, double y) { return new double[]{t[0] * x + t[1] * y + t[4], t[2] * x + t[3] * y + t[5]}; }
        /** The box {@code t} carries the box {@code x0, y0} to {@code x1, y1} to: {lowest x, lowest y, highest x, highest y}. */
        static double[] box(double[] t, double x0, double y0, double x1, double y1) {
            double[] b = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
            for (int c = 0; c < 4; c++) {
                double[] p = apply(t, (c & 1) == 0 ? x0 : x1, (c & 2) == 0 ? y0 : y1);
                b[0] = Math.min(b[0], p[0]); b[1] = Math.min(b[1], p[1]); b[2] = Math.max(b[2], p[0]); b[3] = Math.max(b[3], p[1]);
            }
            return b;
        }
        /** {@code t}'s inverse. */
        static double[] inverse(double[] t) {
            double det = t[0] * t[3] - t[1] * t[2], a = t[3] / det, b = -t[1] / det, c = -t[2] / det, d = t[0] / det;
            return new double[]{a, b, c, d, -(a * t[4] + b * t[5]), -(c * t[4] + d * t[5])};
        }
        /**
         * A pen's tilt (signed degrees on each axis, {@link BrushDirection}) as {@code t} turns it: its lean's direction
         * mirrored with the page, how far it leans kept; NaN stays NaN.
         */
        static float[] tilt(double[] t, float tiltX, float tiltY) {
            if (!Float.isFinite(tiltX) || !Float.isFinite(tiltY)) return new float[]{tiltX, tiltY};
            double sx = Math.tan(Math.toRadians(Math.max(-89.9, Math.min(89.9, tiltX)))), sy = Math.tan(Math.toRadians(Math.max(-89.9, Math.min(89.9, tiltY))));
            double nx = t[0] * sx + t[1] * sy, ny = t[2] * sx + t[3] * sy;
            return new float[]{(float) Math.toDegrees(Math.atan(nx)), (float) Math.toDegrees(Math.atan(ny))};
        }
        /** Every image of {@code x, y}, the point itself first: where a fill tap fills with Copy Strokes ({@code FillTool.Areas}). */
        double[][] points(double x, double y) {
            double[][] out = new double[maps.length][];
            for (int i = 0; i < maps.length; i++) out[i] = apply(maps[i], x, y);
            return out;
        }
    }

    /**
     * Real Mirror's view of the page: each pixel shows the kept cell's pixel its map comes from, and the pen anywhere
     * draws there ({@link #fold}, {@link #leaves}; {@link MirrorStroke}). The kept point's images
     * ({@link #ox}, {@link #oy}), one a map; a pixel nearest image {@code k} lies in cell {@code k} (the cells are the
     * group's: each the images' Voronoi cell, convex), whose map {@code k} carried the kept cell there, so it shows
     * the page at {@code inverse[k]} of it. Cell 0 is the kept one and shows itself.
     */
    static final class View {
        final double[][] maps, inverse;
        final double[] ox, oy;
        View(Group g, double kx, double ky) {
            maps = g.maps;
            inverse = new double[maps.length][];
            ox = new double[maps.length]; oy = new double[maps.length];
            for (int k = 0; k < maps.length; k++) {
                inverse[k] = Group.inverse(maps[k]);
                double[] p = Group.apply(maps[k], kx, ky); ox[k] = p[0]; oy[k] = p[1];
            }
        }
        /** The cell holding {@code x, y}: the nearest image of the kept point (0, the kept cell, on a tie). */
        int cell(double x, double y) {
            int best = 0;
            double d = sq(x - ox[0]) + sq(y - oy[0]);
            for (int k = 1; k < ox.length; k++) { double e = sq(x - ox[k]) + sq(y - oy[k]); if (e < d - 1e-9) { d = e; best = k; } }
            return best;
        }
        private static double sq(double v) { return v * v; }
        /** Whether page pixel {@code x, y} is in the kept cell, which holds every mark the view shows. */
        boolean kept(double x, double y) { return cell(x, y) == 0; }
        /** {@code x, y}, in cell {@code k}, folded into the kept cell: its image under the inverse of map {@code k}, the point it shows. */
        double[] fold(int k, double x, double y) { return Group.apply(inverse[k], x, y); }
        /**
         * Where the segment from {@code ax, ay} to {@code bx, by} leaves cell {@code k}, which it is in at {@code from}
         * (0 at a, 1 at b): {t, the cell it enters}; null when it stays in to b. Cell {@code k} is where each other
         * image's half-plane (its side of the line midway between the two) keeps it, so the segment leaves where it first
         * crosses one; it never comes back, the cell being convex. Through a crossing of mirrors it leaves for each cell
         * there in turn, at the same t.
         */
        double[] leaves(int k, double ax, double ay, double bx, double by, double from) {
            double dx = bx - ax, dy = by - ay, best = Double.POSITIVE_INFINITY;
            int next = -1;
            for (int j = 0; j < ox.length; j++) {
                double nx = ox[j] - ox[k], ny = oy[j] - oy[k], slope = nx * dx + ny * dy;
                if (j == k || slope <= 1e-12) continue;
                double t = Math.max(from, ((ox[j] + ox[k]) / 2 - ax) * nx / slope + ((oy[j] + oy[k]) / 2 - ay) * ny / slope);
                if (t < best) { best = t; next = j; }
            }
            return best < 1 ? new double[]{best, next} : null;
        }
        /**
         * Row {@code y}'s pixels {@code x0} to {@code x1} (exclusive) of a {@code width × height} page: each one's source
         * pixel (an index on the page, itself in the kept cell, −1 where it comes from off the page) into {@code out}
         * from 0. Each cell crosses a row once (the cells are convex), so a row is a few runs, their ends found by halving.
         */
        void row(int y, int x0, int x1, int width, int height, int[] out) {
            double cy = y + .5;
            for (int x = x0; x < x1; ) {
                int k = cell(x + .5, cy);
                // The run's end, the first pixel in another cell: doubling past it, then halving back.
                int good = x, step = 1, probe = Math.min(x1, x + 1);
                while (probe < x1 && cell(probe + .5, cy) == k) { good = probe; step <<= 1; probe = Math.min(x1, good + step); }
                int lo = good + 1, hi = probe;
                while (lo < hi) { int mid = (lo + hi) >>> 1; if (cell(mid + .5, cy) == k) lo = mid + 1; else hi = mid; }
                int end = lo;
                double[] t = inverse[k];
                for (int px = x; px < end; px++) {
                    if (k == 0) { out[px - x0] = y * width + px; continue; }
                    double cx = px + .5;
                    int sx = (int) Math.floor(t[0] * cx + t[1] * cy + t[4]), sy = (int) Math.floor(t[2] * cx + t[3] * cy + t[5]);
                    out[px - x0] = sx < 0 || sy < 0 || sx >= width || sy >= height ? -1 : sy * width + sx;
                }
                x = end;
            }
        }
    }

    // The MIRR section

    /**
     * The section's version: a reader skips a later one (the page opens without mirrors). 1: on, Real Mirror; 2 (the
     * guides hidden, the only state 1 can't hold): the guides too. Each page writes the lowest that holds it, so one with
     * its guides showing writes as before, byte for byte, and opens with its mirrors in the version before Show Guides.
     */
    static final int VERSION = 2;
    /** Damage beyond this many bytes is refused. */
    static final int MOST_BYTES = 1 + 1 + 16 + 1 + MOST * 24;
    /** Version, flags (on, Real Mirror, the guides hidden), the kept point, the count, then each mirror's point and angle. */
    byte[] encode() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeByte(guides ? 1 : 2);
            out.writeByte((on ? 1 : 0) | (style == Style.REAL ? 2 : 0) | (guides ? 0 : 4));
            out.writeDouble(keepX); out.writeDouble(keepY);
            out.writeByte(lines.size());
            for (Line m : lines) { out.writeDouble(m.x); out.writeDouble(m.y); out.writeDouble(m.angle); }
            out.flush();
            return bytes.toByteArray();
        } catch (IOException e) { throw new IllegalStateException(e); }
    }
    /** A MIRR section read back; a later version's is {@link #NONE}; damage refused. */
    static Mirrors decode(byte[] section, int width, int height) throws IOException {
        if (section.length < 1 || section.length > MOST_BYTES) throw new IOException("Invalid mirror section");
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(section))) {
            int version = in.readUnsignedByte();
            if (version < 1 || version > VERSION) return NONE;
            int flags = in.readUnsignedByte();
            double kx = in.readDouble(), ky = in.readDouble();
            int count = in.readUnsignedByte();
            if (flags > (version == 1 ? 3 : 7) || count < 1 || count > MOST || section.length != 19 + 24 * count) throw new IOException("Invalid mirror section");
            ArrayList<Line> lines = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                double x = in.readDouble(), y = in.readDouble(), a = in.readDouble();
                if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(a) || Math.abs(x) > 4 * width || Math.abs(y) > 4 * height) throw new IOException("Invalid mirror");
                lines.add(new Line(x, y, a));
            }
            if (Double.isNaN(kx) != Double.isNaN(ky) || Double.isInfinite(kx) || Double.isInfinite(ky)) throw new IOException("Invalid mirror section");
            return new Mirrors(lines, (flags & 1) != 0, (flags & 4) == 0, (flags & 2) != 0 ? Style.REAL : Style.COPY, kx, ky);
        }
    }

    @Override public boolean equals(Object o) {
        if (!(o instanceof Mirrors)) return false;
        Mirrors m = (Mirrors) o;
        return m.on == on && m.guides == guides && m.style == style && m.lines.equals(lines) && Double.compare(m.keepX, keepX) == 0 && Double.compare(m.keepY, keepY) == 0;
    }
    @Override public int hashCode() { return lines.hashCode() * 31 + (on ? 1 : 0) + style.ordinal() * 2 + (guides ? 0 : 4); }
}
