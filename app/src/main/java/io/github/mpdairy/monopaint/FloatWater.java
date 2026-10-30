package io.github.mpdairy.monopaint;

/**
 * A Float bath on the page (the Float spec, "The bath: same layer only"): the floating paint ({@link FloatBath}) over
 * the layer it floats on, as {@link DryingWater} is a Slow wash's. The layer's paint under the water is read once, as
 * found, tile by tile as the bath first reaches it, and the bath is shown as that paint darkened by the film
 * ({@link FloatScan}): premultiplied, {@code p' = p + f (1 − p)}, coverage likewise, so floating paint darkens what is
 * beneath and never moves it, and clear water shows it through. Printing ({@link #print}) writes the film once more, the
 * paper's tooth taking a touch less in its valleys, and the bath is gone.
 * <p>
 * Undo: the bath is one undo step from its first stroke to its print. Its first change is a gesture of its own; every
 * later stroke, tick and the print continue it ({@link ToneDocument#finishContinuation}), so one Undo after the print
 * takes back the whole sheet. While it floats, Undo takes back its last stroke ({@link #undo}): the rings as they were
 * ({@link FloatBath#undoLast}), shown again in the same step; the first stroke's Undo is the page's own.
 * <p>
 * Writes go through {@link ToneDocument#smearTones} (a coverage floor, so a ring moving away leaves the layer as it was
 * found, never a white patch over the layers below), on the page's thread, in a gesture someone has begun; what a show
 * writes is worked out first ({@link #tones}). A tick's drift and show, and the print, are worked out off the page's
 * thread ({@link #work}, {@link #printing}; {@link SettleWorker}) and then written ({@link #show}). Pure.
 */
final class FloatWater {
    /** The page's undo tile, which dry paint is read in. */
    private static final int SIDE = ToneDocument.TILE;
    /** Showing samples a pixel row: the full show and print, and the quick one under the pen and while it drifts. */
    static final int FULL = 4, QUICK = 1;
    /** Drift at Drift 1 on an upright page, page px a second (the spec's 40 px/s). */
    static final double DRIFT_PX_S = 40;

    final ToneDocument document;
    final int layer;
    private long revision;
    final FloatBath bath = new FloatBath();
    /** The paper the print's tooth is read from. */
    private final StrokePaper paper;
    /** The last stroke's floating time and drift; the ticks since the last touch. */
    long floatMs = PartDial.wetMs(.6);
    double drift = .5;
    int age;
    /**
     * How much of the print the eraser takes: the least Erasable of the strokes floating here ({@link #floats}). Only the
     * print lays paint that stays; a show lays nothing of its own, so the layer keeps what it kept as found.
     */
    private double erasable = 1;
    /**
     * The kinds of mark the strokes floating here lay ({@link #floats}). Inkwash: the first stroke's with paint of its own,
     * so every show and the print lay on one plane ({@link ToneDocument#planeOf}), whatever floats after it.
     */
    private int kind;
    /** Inkwash: the kinds every gesture on it begins with, the strokes floating on it too ({@link ModularStroke#begin}). */
    int kinds() { return kind; }
    /** Whether an undo step holds the bath (its first change made one, and no Undo has emptied it). */
    private boolean edit;
    /** The box the bath last showed in, committed (page px, inclusive), or null; and what the open gesture has written beyond it. */
    private int[] shown, written;
    /** The layer under the water as found, per undo tile: tones over white and coverages, or null until first reached. */
    private final int[][] dryTone, dryCov;
    /** Each undo tile the bath has ever written film into: elsewhere a pixel with no film is as found, and is passed over. */
    private final boolean[] inked;
    private final int columns;
    /** A show's film, kept from {@link #prepare} for the {@link #full} that follows, with the box it covers. */
    private float[] prepared;
    private int[] preparedAt;

    /** A bath on {@code document}'s active layer, which a Float stroke has just selected. */
    FloatWater(ToneDocument document, StrokePaper paper) {
        this.document = document; this.paper = paper;
        layer = document.activeLayer(); revision = document.revision();
        columns = (document.width + SIDE - 1) / SIDE;
        int tiles = columns * ((document.height + SIDE - 1) / SIDE);
        dryTone = new int[tiles][]; dryCov = new int[tiles][]; inked = new boolean[tiles];
    }

    /** Still the page it floats on: the same document, revision and active layer, shown (as {@link DryingWater#valid}). */
    boolean valid(ToneDocument d) {
        return d == document && d.revision() == revision && d.activeLayer() == layer && d.layerVisible(layer);
    }
    /** A stroke whose paint the eraser takes {@code erasable} of joins the bath. */
    void floats(double erasable, int kind) { this.erasable = Math.min(this.erasable, erasable); if (this.kind == 0) this.kind = kind; }
    /** Whether its paint drifts on a tilted page. */
    boolean drifts() { return drift > 0; }

    /**
     * The most page pixels the bath's box may cover, and the most points its rings may hold. A drop moves every ring, so
     * each stroke shows the whole bath again and the print ahead works it all out (about 8 ms a million pixels on a Mac,
     * some 20 times that on the Manta); a drag moves and refines every point near it each few steps (about 2 ms per
     * 100,000 points on a Mac). Past either the bath prints at the stroke's lift ({@link WatercolorSession#floatLifted})
     * and the next drop starts a new one, so a stroke's work can't grow with the bath (the owner, 2026-10-22: "i think it
     * gets overloaded").
     */
    static final long MOST_PX = 1L << 20;
    static final int MOST_POINTS = 50_000;
    /** Whether the bath covers more than {@link #MOST_PX} or holds more than {@link #MOST_POINTS}. */
    boolean tooBig() {
        int[] b = box();
        return b != null && ((long) (b[2] - b[0] + 1) * (b[3] - b[1] + 1) > MOST_PX || bath.points > MOST_POINTS);
    }
    /** The rings' box on the page, a pixel wider each way and clipped, or null when there is none. */
    int[] box() {
        float[] b = bath.box();
        if (b == null) return null;
        int[] r = {Math.max(0, (int) Math.floor(b[0]) - 1), Math.max(0, (int) Math.floor(b[1]) - 1),
                Math.min(document.width - 1, (int) Math.ceil(b[2]) + 1), Math.min(document.height - 1, (int) Math.ceil(b[3]) + 1)};
        return r[2] < r[0] || r[3] < r[1] ? null : r;
    }
    /**
     * Where a stroke's show must reach: where the bath changed since it last showed ({@link FloatBath#takeMoved}) and
     * what the gesture wrote meanwhile; {@link #reach()} after a drift or an Undo. Null for nowhere.
     */
    int[] changed() {
        boolean[] all = {false};
        float[] m = bath.takeMoved(all);
        if (all[0]) return reach();
        int[] r = null;
        if (m != null) {
            r = new int[]{Math.max(0, (int) Math.floor(m[0])), Math.max(0, (int) Math.floor(m[1])), Math.min(document.width - 1, (int) Math.ceil(m[2])), Math.min(document.height - 1, (int) Math.ceil(m[3]))};
            if (r[2] < r[0] || r[3] < r[1]) r = null;
        }
        if (written != null) r = r == null ? written.clone() : StrokeMath.union(r, written);
        return r;
    }
    /** Where a whole show must reach: the rings' box, where they showed before and what the gesture wrote meanwhile. */
    int[] reach() {
        int[] r = box();
        if (shown != null) r = r == null ? shown.clone() : StrokeMath.union(r, shown);
        if (written != null) r = r == null ? written.clone() : StrokeMath.union(r, written);
        return r;
    }

    // ---- Showing ----

    /** The film over {@code at} (page px, inclusive), {@code samples} rows a pixel row, into a new array (any thread). */
    float[] film(int[] at, int samples) {
        float[] out = new float[(at[2] - at[0] + 1) * (at[3] - at[1] + 1)];
        FloatScan.film(bath.rings, at[0], at[1], at[2], at[3], samples, out);
        return out;
    }
    /**
     * The film over {@code at} as the full show and the print lay it: four samples a row, its edges softened by a grey
     * (real ink's edge isn't cut by a knife) and a faint grain of the ink in it.
     */
    float[] fullFilm(int[] at) {
        float[] f = film(at, FULL);
        int w = at[2] - at[0] + 1, h = at[3] - at[1] + 1;
        soften(f, w, h);
        Cores.inBands(0, h - 1, 32, (long) w * h, (top, bottom, worker) -> {
            for (int y = top, i = top * w; y <= bottom; y++) for (int x = 0; x < w; x++, i++) if (f[i] > 0) f[i] *= (float) (1 - GRAIN + GRAIN * paper.mid(at[0] + x, at[1] + y));
        });
        return f;
    }
    /** How much the ink's grain takes from the film at most (a share), and how much of each pixel's film its edge softening moves to each neighbour. */
    static final double GRAIN = .1, SOFT = .12;
    /** {@code f} ({@code w × h}) blurred a little each way: [SOFT, 1 − 2 SOFT, SOFT], so a crisp edge gains one in-between grey. */
    static void soften(float[] f, int w, int h) {
        float a = (float) SOFT, b = 1 - 2 * a;
        // Rows, then columns, each in pieces over the cores: every row (column) is blurred on its own.
        int piece = 64, rows = (h + piece - 1) / piece, columns = (w + piece - 1) / piece;
        boolean share = (long) w * h >= Cores.SHARE_PX;
        Cores.Piece across = (k, worker) -> {
            float[] row = new float[w];
            for (int y = k * piece; y < Math.min(h, (k + 1) * piece); y++) {
                int o = y * w;
                System.arraycopy(f, o, row, 0, w);
                for (int x = 0; x < w; x++) f[o + x] = b * row[x] + a * (row[Math.max(0, x - 1)] + row[Math.min(w - 1, x + 1)]);
            }
        };
        Cores.Piece down = (k, worker) -> {
            float[] up = new float[piece], here = new float[piece];
            int x0 = k * piece, n = Math.min(w, x0 + piece) - x0;
            System.arraycopy(f, x0, up, 0, n);
            for (int y = 0; y < h; y++) {
                System.arraycopy(f, y * w + x0, here, 0, n);
                int below = Math.min(h - 1, y + 1) * w + x0;
                for (int x = 0; x < n; x++) f[y * w + x0 + x] = b * here[x] + a * (up[x] + f[below + x]);
                float[] t = up; up = here; here = t;
            }
        };
        if (share) { Cores.each(rows, across); Cores.each(columns, down); }
        else { for (int k = 0; k < rows; k++) across.run(k, 0); for (int k = 0; k < columns; k++) down.run(k, 0); }
    }
    /** Off the page's thread, before a {@link #full} show: its film worked out now, so the page's thread only writes it. */
    void prepare() {
        int[] at = changed();
        preparedAt = at;
        prepared = at == null ? null : fullFilm(at);
    }

    /**
     * Shows the whole bath (the print with {@code print}) in the open gesture: its film over {@link #reach}, from
     * {@link #prepare} if that was the last change. Afterwards it has shown in its box (none once printed).
     */
    void full(boolean print) {
        // A stroke's show reaches where the bath changed ({@link #prepare} worked it out off the page's thread); the print, all of it.
        boolean ready = !print && prepared != null;
        int[] at = print ? reach() : ready ? preparedAt : changed();
        if (print) bath.takeMoved(new boolean[1]);
        if (at != null) write(at, ready ? prepared : fullFilm(at), print);
        prepared = null; preparedAt = null;
        shown = print ? null : box();
    }
    /**
     * Writes {@code film} (darkness 0–1 over {@code at}, row by row) over the layer as found, in the open gesture: the
     * print's with {@code print}. What the gesture writes beyond the bath's box is kept, so the next full show covers it.
     */
    void write(int[] at, float[] film, boolean print) {
        StrokeTones t = tones(at, film, print, true);
        if (t != null) t.applyTo(document, null);
    }
    /**
     * What {@link #write} writes, kept off the page ({@link StrokeTones#smeared}; null: nothing), on any thread while the
     * page doesn't change: {@code film} over the layer as found. With {@code keep}, as written (what it wrote noted for
     * the next show, the tiles it inked kept as inked, the layer as found kept as read); without, only worked out,
     * changing nothing here (the print ahead of a tick, which may be let go while a stroke changes the page).
     */
    StrokeTones tones(int[] at, float[] film, boolean print, boolean keep) {
        int x0 = Math.max(0, at[0]), y0 = Math.max(0, at[1]), x1 = Math.min(document.width - 1, at[2]), y1 = Math.min(document.height - 1, at[3]);
        if (x1 < x0 || y1 < y0) return null;
        int[][] dryTone = keep ? this.dryTone : this.dryTone.clone(), dryCov = keep ? this.dryCov : this.dryCov.clone();
        fillDry(x0, y0, x1, y1, dryTone, dryCov);
        int rw = x1 - x0 + 1, fw = at[2] - at[0] + 1;
        StrokeTones out = StrokeTones.smeared(document);
        boolean[] inked = keep ? this.inked : this.inked.clone();
        // In bands of whole undo tile rows, a big one on several cores (each keeps only its own tiles' pixels).
        long area = (long) rw * (y1 - y0 + 1);
        Cores.inBands(y0, y1, SIDE, area, (top, bottom, worker) -> {
            // Erasable: the print's film is its own paint (owns); a show's lays none.
            int[] grays = new int[rw], covs = new int[rw], owns = new int[rw];
            for (int y = top; y <= bottom; y++) {
                int ty = y / SIDE, row = (y % SIDE) * SIDE;
                for (int x = x0, i = 0; x <= x1; x++, i++) {
                    int k = ty * columns + x / SIDE, o = row + x % SIDE;
                    int tone = dryTone[k][o], cov = dryCov[k][o];
                    double f = film[(y - at[1]) * fw + x - at[0]];
                    if (print && f > 0) f *= tooth(x, y);
                    owns[i] = 0;
                    if (!(f > 0)) { if (inked[k]) { grays[i] = tone; covs[i] = cov; } else grays[i] = -1; continue; }
                    inked[k] = true;
                    double p = (255 - tone) / 255.0, a = cov / 255.0;
                    p += f * (1 - p); a += f * (1 - a);
                    grays[i] = (int) Math.round(255 * (1 - p)); covs[i] = (int) Math.round(255 * a);
                    if (print) owns[i] = (int) Math.round(255 * Math.min(1, f));
                }
                out.smearTones(x0, y, rw, 1, grays, covs, owns);
            }
        });
        if (keep) {
            int[] w = {x0, y0, x1, y1};
            written = written == null ? w : StrokeMath.union(written, w);
        }
        return out;
    }
    /** How much of the film the paper takes at {@code x, y}: a touch less in its valleys, a little unevenly. */
    private double tooth(int x, int y) { return .9 + .1 * paper.fine(x, y); }

    /** Reads the layer as found into every undo tile {@code x0..x1, y0..y1} touches that hasn't been read, into {@code dryTone} and {@code dryCov}. */
    private void fillDry(int x0, int y0, int x1, int y1, int[][] dryTone, int[][] dryCov) {
        for (int ty = y0 / SIDE; ty <= y1 / SIDE; ty++) for (int tx = x0 / SIDE; tx <= x1 / SIDE; tx++) {
            int k = ty * columns + tx;
            if (dryTone[k] != null) continue;
            int left = tx * SIDE, top = ty * SIDE, w = Math.min(SIDE, document.width - left), h = Math.min(SIDE, document.height - top);
            int[] t = new int[w * h], c = new int[w * h], tt = new int[SIDE * SIDE], cc = new int[SIDE * SIDE];
            document.strokeBasePixels(left, top, w, h, t, c);
            for (int y = 0; y < h; y++) { System.arraycopy(t, y * w, tt, y * SIDE, w); System.arraycopy(c, y * w, cc, y * SIDE, w); }
            dryTone[k] = tt; dryCov[k] = cc;
        }
    }

    /**
     * Ends the open gesture as the bath's undo step: its first change a step of its own, every later one a continuation
     * of it. Keeps the page's revision, so the bath stays valid. Returns whether the page changed.
     */
    boolean commit() {
        int steps = document.undoSteps();
        boolean changed;
        if (!edit) changed = edit = document.finish();
        else if ((changed = document.finishContinuation()) && document.undoSteps() < steps) edit = false;
        written = null;
        revision = document.revision();
        return changed;
    }

    // ---- Life ----

    /** A Float stroke on it ended: it floats for {@code floatMs} from now, drifting at {@code drift}. */
    void touched(long floatMs, double drift) { this.floatMs = floatMs; this.drift = drift; age = 0; }

    /**
     * One tick ({@link DryingWater#TPS} a second, between strokes): prints once its floating time has passed since the
     * last touch; else, on a tilted page, drifts and shows quick. False once printed.
     */
    boolean tick(PageTilt tilt) {
        Shown s = work(tilt);
        show(s);
        if (s.print) clear();
        return !s.print;
    }
    /** Prints it onto the paper, in a gesture continuing its step: the rings' greys, then nothing floats. */
    void print() {
        show(printing());
        clear();
    }
    /** What a tick or the print changes on the page, worked out off it ({@link #work}, {@link #printing}), for {@link #show} to write. */
    static final class Shown {
        /** The tones a show writes (null: none), and the box the bath shows in after it (null once printed). */
        final StrokeTones tones;
        final int[] box;
        /** Whether it is the print (nothing floats after), and whether the bath moved (what printing now prints did too). */
        final boolean print, moved;
        Shown(StrokeTones tones, int[] box, boolean print, boolean moved) { this.tones = tones; this.box = box; this.print = print; this.moved = moved; }
    }
    /** A tick that changes nothing on the page: a level page, no drift, or nothing floating. */
    private static final Shown STILL = new Shown(null, null, false, false);
    /**
     * One tick's work ({@link #tick}), on any thread while nothing else changes the page or the bath: once its floating
     * time has passed, the print ({@link #printing}); else on a tilted page the drift and its quick show (one sample a
     * row) over where the bath was and is, worked out.
     */
    Shown work(PageTilt tilt) {
        age++;
        if (age * 1000L / DryingWater.TPS >= floatMs) return printing();
        if (tilt.level() || !(drift > 0) || bath.rings.isEmpty()) return STILL;
        int[] b = box();
        if (b == null) return STILL;
        double gx = tilt.ux(), gy = tilt.uy(), cx = (b[0] + b[2]) / 2.0, cy = (b[1] + b[3]) / 2.0;
        double hw = Math.max(20, Math.abs((b[2] - b[0]) * gy) / 2 + Math.abs((b[3] - b[1]) * gx) / 2);
        bath.apply(new FloatBath.Drift(gx, gy, DRIFT_PX_S / DryingWater.TPS * drift * tilt.m, cx, cy, hw));
        bath.flush();
        int[] at = reach();
        bath.takeMoved(new boolean[1]);
        return new Shown(at == null ? null : tones(at, film(at, QUICK), false, true), box(), false, true);
    }
    /**
     * The print worked out (any thread, while nothing else changes the page or the bath): the film over all the bath
     * showed and shows, four samples a row, as {@link #full} prints it. It changes nothing, so it can be worked out ahead
     * of a tick, and the bath float on.
     */
    Shown printing() { return printing(reach()); }
    /**
     * The print as it will be once {@code tick} (a drift worked out, not yet shown) is shown: over the box it then shows
     * in, all a print reaches then. So the print after a drift can be worked out with it, before either is written.
     */
    Shown printing(Shown tick) { return printing(tick.box); }
    private Shown printing(int[] at) {
        int asked = stops;
        float[] film = at == null ? null : fullFilm(at);
        // Let go meanwhile (a stroke joins the bath): no need to work out what it would write.
        if (stops != asked) throw new java.util.concurrent.CancellationException();
        return new Shown(at == null ? null : tones(at, film, true, false), null, true, true);
    }
    /** Work on the bath let go there ({@link WatercolorSession}): a print being worked out stops before its writes. */
    void stop() { stops++; }
    private volatile int stops;
    /**
     * Writes a tick's or the print's work ({@link #work}, {@link #printing}) on the page's thread, in a gesture continuing
     * the bath's step. After the print nothing is shown; {@link #clear} empties the bath once no work reads it.
     */
    void show(Shown s) {
        if (s == STILL) return;
        document.begin(s.print ? erasable : 1, kind); // Inkwash: the films float on the plane the print lays on
        if (s.tones != null) s.tones.applyTo(document, null);
        if (s.print) { prepared = null; preparedAt = null; }
        shown = s.box;
        commit();
    }
    /** After the print: nothing floats. */
    private void clear() {
        bath.takeMoved(new boolean[1]);
        bath.rings.clear();
    }
    /**
     * Undo while it floats: its last stroke taken back. With more than one, the rings as before it, shown again in its
     * step, floating on (its time starts again); returns true. With one, the page's own Undo takes the bath's step back;
     * returns false: nothing floats.
     */
    boolean undo() {
        if (bath.strokes() > 1) {
            bath.undoLast();
            document.begin(1, kind);
            full(false);
            commit();
            age = 0;
            return true;
        }
        if (edit) document.undo();
        edit = false;
        bath.undoLast();
        return false;
    }
}
