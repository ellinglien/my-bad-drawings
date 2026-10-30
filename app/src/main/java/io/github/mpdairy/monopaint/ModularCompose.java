package io.github.mpdairy.monopaint;

import static io.github.mpdairy.monopaint.StrokeMath.clamp;
import static io.github.mpdairy.monopaint.StrokeMath.lerp;
import static io.github.mpdairy.monopaint.StrokeMath.smoothstep;

/**
 * A six-part stroke's tones over the tones the gesture found (stroke.js 396–441), written to the page: transparent
 * paint and ink only darken; opaque paint mixes toward its own grey and covers the layers below; impasto adds its
 * relief. One per stroke, so its rows are reused, never made per sample. A pixel's tone depends only on what the
 * gesture found there and its line, body and relief, so a pixel whose line and body are as they were when it was
 * last composed is left as it is. A big rectangle is composed in bands of tile rows on several cores ({@link Cores},
 * {@link ToneDocument#openBands}); a pixel's tone doesn't depend on which band or core composed it. It composes onto a
 * {@link Page}: the page itself ({@link #of}), or a quick stroke's tones composed ahead of its lift ({@link StrokeTones}).
 * Blend ({@link ResolvedBrush.BlendMode}) changes how the mark meets what is there, from the mark's own darkness over
 * white (the tone it would lay on blank paper): White moves the tone toward white by it, Invert toward the opposite of
 * the page under it ({@link Under}), both laying coverage over what is under them as opaque paint does; Behind scales
 * the mark by how blank the page under it is. Resist ({@link ResolvedBrush#resist}) works from the mark's darkness too:
 * onto the page's masking fluid it lays that darkness (the more of what is there and it), which shields every layer
 * from what comes later; wax, the part the eraser can't lift, catches only the paper's high points, and its faint sheen
 * goes on the pair's ink layer ({@link #sheen}). Screen and Tone ({@link PrintScreen}) take the mark's darkness over white
 * through a threshold fixed to the page as each pixel is written: pure ink, or the page as found. Pure, checked on the desktop.
 */
final class ModularCompose {
    /**
     * What a compose reads the tones the gesture found from and writes the stroke's tones to: the page
     * ({@link #of}: {@link ToneDocument}'s own calls) or {@link StrokeTones}.
     */
    interface Page {
        int width();
        int height();
        /** {@link ToneDocument#strokeBaseTones}. */
        void strokeBaseTones(int x, int y, int w, int h, int[] out);
        /** {@link ToneDocument#strokeBasePixels} (a smudging stroke's compose, on the page only). */
        void strokeBasePixels(int x, int y, int w, int h, int[] tones, int[] coverages);
        /** {@link ToneDocument#touched}. */
        boolean touched(int x, int y);
        /** Whether page pixel {@code x, y} shows anything but {@code found}, the tone the gesture found there (a tail taken back, to restore). */
        boolean differs(int x, int y, int found);
        /** {@link ToneDocument#restoreSpan}. */
        void restoreSpan(int x, int end, int y);
        /**
         * {@link ToneDocument#setTones(int, int, int, int, int[], int[])}: {@code owns} (or null) each pixel's own
         * darkness over white paper, of which the gesture's permanent share is what the eraser will leave.
         */
        void setTones(int x, int y, int w, int h, int[] grays, int[] owns);
        /** {@link ToneDocument#coverTones(int, int, int, int, int[], int[], int[])}; a negative coverage is a pixel without paint (as 0). */
        void coverTones(int x, int y, int w, int h, int[] grays, int[] coverages, int[] owns);
        /** {@link ToneDocument#smearTones(int, int, int, int, int[], int[], int[])} (a smudging stroke's compose, on the page only). */
        void smearTones(int x, int y, int w, int h, int[] grays, int[] coverages, int[] owns);
        /** {@link ToneDocument#openBands}, {@link ToneDocument#closeBands}. */
        void openBands();
        void closeBands();
        /** {@link ToneDocument#strokeBasePrints}: a screened stroke's print tone as found (Screen, {@link ResolvedBrush#prints}). */
        void strokeBasePrints(int x, int y, int w, int[] out);
        /** {@link ToneDocument#setPrints}. */
        void setPrints(int x, int y, int w, int[] prints);
    }
    /** The page itself: composing writes {@code document}. */
    static Page of(ToneDocument document) {
        return new Page() {
            @Override public int width() { return document.width; }
            @Override public int height() { return document.height; }
            @Override public void strokeBaseTones(int x, int y, int w, int h, int[] out) { document.strokeBaseTones(x, y, w, h, out); }
            @Override public void strokeBasePixels(int x, int y, int w, int h, int[] tones, int[] coverages) { document.strokeBasePixels(x, y, w, h, tones, coverages); }
            @Override public boolean touched(int x, int y) { return document.touched(x, y); }
            @Override public boolean differs(int x, int y, int found) { return document.tone(x, y) != found; }
            @Override public void restoreSpan(int x, int end, int y) { document.restoreSpan(x, end, y); }
            @Override public void setTones(int x, int y, int w, int h, int[] grays, int[] owns) { document.setTones(x, y, w, h, grays, owns); }
            @Override public void coverTones(int x, int y, int w, int h, int[] grays, int[] coverages, int[] owns) { document.coverTones(x, y, w, h, grays, coverages, owns); }
            @Override public void smearTones(int x, int y, int w, int h, int[] grays, int[] coverages, int[] owns) { document.smearTones(x, y, w, h, grays, coverages, owns); }
            @Override public void openBands() { document.openBands(); }
            @Override public void closeBands() { document.closeBands(); }
            @Override public void strokeBasePrints(int x, int y, int w, int[] out) { document.strokeBasePrints(x, y, w, out); }
            @Override public void setPrints(int x, int y, int w, int[] prints) { document.setPrints(x, y, w, prints); }
        };
    }
    /** PREVIEW: Drying's body while the pen is down. FINAL: as it stays. */
    enum Phase { PREVIEW, FINAL }
    /** Whether a pixel as last composed is left as it is; off only in the check that composing every pixel draws the same. */
    static boolean skipUnchanged = true;

    private Page page;
    /** The page's size. */
    private final int width, height;
    private final ResolvedBrush r;
    private final StrokePaper paper;
    /** Charcoal's own grain, or null. */
    private final StickGrain grain;
    /** Charcoal's contact rule, its brush's terms worked out once; null for other media. */
    private final Contact contact;
    /**
     * Each pixel's line and body as last composed (0 where never), and the phase and body buffer they were composed in;
     * null once every pixel is composed. A big stroke's ({@link #composeLattice}) are kept on its lattice, with its relief.
     */
    private FloatTiles shownLine, shownBody, shownRelief;
    private Phase shownPhase;
    private FloatTiles shownFrom;
    /** Set once a compose reads another phase or body buffer (Drying's lift), takes every pixel or is {@link #release}d: what was shown no longer tells, so every pixel is composed. */
    private boolean mixed;
    /** Each worker's ({@link Cores}) rows: what composing reads and writes, made on first use. */
    private final Rows[] rows = new Rows[Cores.WORKERS];
    /** {@link #composeLattice}: which lattice points changed since last composed (1), a row of the rectangle's lattice at a time, and each row's first and last. */
    private byte[] changed;
    private int[] changedFirst, changedLast;
    /**
     * A smudging stroke's plane ({@link CarryPlane}), or null: where it is filled, what lies under the stroke is the
     * plane (the page as found, as smudged so far), composed even with no line or body there, and written through
     * {@link ToneDocument#smearTones} with the plane's coverage as the floor.
     */
    private CarryPlane plane;
    /** The line and body this compose reads (a print over prints reads a pixel's neighbours: {@link #edge}). */
    private FloatTiles nowLine, nowBody;
    /** The page under a Behind or Invert stroke as found ({@link #under(Under)}), or null. */
    private Under under;
    /**
     * Resist: what the compose writes. False: the masking fluid, onto the page's mask ({@link ToneDocument#beginFluid});
     * true: wax's sheen, onto the pair's ink layer, which its stroke lays first (the mask would shield it).
     */
    private boolean sheen;
    /**
     * Crayon ({@link ResolvedBrush#waxes}): composing its wax onto the page's masking fluid ({@link #waxOnMask}), which
     * its stroke does at the lift after laying the crayon itself on the pair's ink layer: the crayon's darkness over white
     * paper, as strongly as {@link Crayon#mask} says, so washes slide off it.
     */
    private boolean waxMask;
    /** How much darker wax leaves the paper where it lies, at full strength (newmods.js WAX_SHEEN): one grey step. */
    static final double WAX_SHEEN = .07;

    /**
     * The page as a Blend finds it under the stroke (Behind's blankness, Invert's opposite): the composite over
     * {@code paper} of every visible layer from the bottom to {@code top}, the stroke's own layer as its gesture found it
     * ({@link ToneDocument#baseComposite}), read a tile at a time on first touch and kept. What it reads never changes
     * while the gesture lasts (only the stroke's own layer is written, and its found tones are its Undo capture), so it
     * reads the same from a quick stroke's render thread, a lift's or any band (a band's tiles are its own).
     */
    static final class Under {
        private static final int SHIFT = 6, MASK = (1 << SHIFT) - 1;
        private final ToneDocument document;
        private final int top, paper, columns;
        private final byte[][] tiles;
        Under(ToneDocument document, int top, int paper) {
            this.document = document; this.top = top; this.paper = paper;
            columns = (document.width + MASK) >> SHIFT;
            tiles = new byte[columns * ((document.height + MASK) >> SHIFT)][];
        }
        private byte[] tile(int k) {
            byte[] t = tiles[k];
            if (t == null) { t = new byte[ToneDocument.TILE * ToneDocument.TILE]; document.baseComposite(k, top, paper, t); tiles[k] = t; }
            return t;
        }
        /** The tone under page pixel {@code x, y}, 0 to 255. */
        int tone(int x, int y) { return tile((y >> SHIFT) * columns + (x >> SHIFT))[((y & MASK) << SHIFT) | (x & MASK)] & 255; }
        /** {@link #tone} for pixels {@code x .. x + n − 1} of row {@code y}, within one tile, into {@code out} from 0. */
        void row(int x, int n, int y, int[] out) {
            byte[] t = tile((y >> SHIFT) * columns + (x >> SHIFT));
            for (int i = ((y & MASK) << SHIFT) | (x & MASK), o = 0; o < n; i++, o++) out[o] = t[i] & 255;
        }
    }
    /**
     * Whether {@code r}'s compose only darkens the tone it finds by a factor of the mark's own ({@link #tones}: the body's
     * glaze and the line's ink multiply it): transparent (no cover, so no coverage of its own), its own Blend, no relief,
     * Screen, resist or smudge, and not a charcoal stick or a crayon (whose contact reads what is under it). Such a mark laid over
     * other tones darkens them by the same factor ({@link ToneDocument#layAgain}).
     */
    static boolean darkensBy(ResolvedBrush r) {
        return r.blendMode == ResolvedBrush.BlendMode.OWN && !(r.cover > 0) && !(r.look.impasto > 0) && r.screen == null && !r.resist && r.carry == null
                && r.rule != ResolvedBrush.Rule.CHARCOAL && r.rule != ResolvedBrush.Rule.CRAYON && !r.laysLight();
    }
    /** How blank tone {@code v} is for Behind (newmods.js blankOf): 1 on paper to 0 from mid grey down, smoothly. */
    static double blank(double v) { return smoothstep(.55, .96, v / 255); }

    /** One worker's rows. */
    private static final class Rows {
        /**
         * Tones the gesture found, tones to write and coverages to cover by, the coverages found, each pixel's own darkness
         * (Erasable) and its print tone to write (Screen), a page row's worth; the page under a Blend ({@link Under}) and
         * the print tone found, a tile's.
         */
        final int[] under, tones, coverages, found, owns, prints, page, printFound;
        /** The line, body and relief at each pixel of the row, and whether it is composed. */
        final float[] c, d, rel;
        final boolean[] want;
        /** Two lattice rows of the line, body and relief, and of what was shown, for {@link #composeLattice}. */
        final float[] l0, l1, b0, b1, r0, r1, sl, sb, sr;
        Rows(int width, int latticeWidth) {
            under = new int[width]; tones = new int[width]; coverages = new int[width]; found = new int[width]; owns = new int[width]; prints = new int[width];
            page = new int[ToneDocument.TILE]; printFound = new int[ToneDocument.TILE];
            c = new float[width]; d = new float[width]; rel = new float[width]; want = new boolean[width];
            l0 = new float[latticeWidth]; l1 = new float[latticeWidth]; b0 = new float[latticeWidth]; b1 = new float[latticeWidth];
            r0 = new float[latticeWidth]; r1 = new float[latticeWidth]; sl = new float[latticeWidth]; sb = new float[latticeWidth]; sr = new float[latticeWidth];
        }
    }
    private Rows rows(int worker) {
        Rows w = rows[worker];
        if (w == null) rows[worker] = w = new Rows(width, (width + 1) / 2 + 2);
        return w;
    }

    ModularCompose(ToneDocument document, ResolvedBrush r, StrokePaper paper) { this(of(document), r, paper); }
    /** Composing onto {@code page}. */
    ModularCompose(Page page, ResolvedBrush r, StrokePaper paper) {
        this.page = page; this.r = r; this.paper = paper;
        width = page.width(); height = page.height();
        grain = r.rule == ResolvedBrush.Rule.CHARCOAL ? StickGrain.of(r.pen.seed) : null;
        contact = r.rule == ResolvedBrush.Rule.CHARCOAL ? new Contact(r) : null;
        ghosts = r.screen != null && r.screen.misregisters() ? new java.util.IdentityHashMap<>() : null;
    }

    /**
     * Screen's Misregister: each line and body buffer composed with its faint second pass, the buffer again at the
     * screen's offset at {@link PrintScreen#GHOST} of its strength (the more of the two at each pixel), so the screen's
     * threshold inks either pass as the prototype's {@code bit1} does; null without one.
     */
    private final java.util.Map<FloatTiles, FloatTiles> ghosts;
    /** One lattice row of a buffer and of the same row at the offset, for {@link #ghost}. */
    private float[] ghostRow, ghostFrom;
    /** {@code rect} with the pixels its second pass reaches, at the offset. */
    private int[] misregistered(int[] rect) { return new int[]{rect[0], rect[1], rect[2] + r.screen.misX, rect[3] + r.screen.misY}; }
    /** {@code from} with its second pass brought up to date over {@code rect} (page pixels, inclusive), kept for the stroke. */
    private FloatTiles ghost(FloatTiles from, int[] rect) {
        FloatTiles g = ghosts.get(from);
        if (g == null) ghosts.put(from, g = new FloatTiles(width, height, from.fill, from.shift));
        int s = from.shift, half = (1 << s) >> 1, dx = (r.screen.misX + half) >> s, dy = (r.screen.misY + half) >> s;
        int x0 = Math.max(0, rect[0]) >> s, x1 = Math.min(width - 1, rect[2]) >> s, y0 = Math.max(0, rect[1]) >> s, y1 = Math.min(height - 1, rect[3]) >> s;
        x1 = Math.min(x1 + 1, from.latticeWidth - 1); y1 = Math.min(y1 + 1, from.latticeHeight - 1);
        int n = x1 - x0 + 1;
        if (n <= 0) return g;
        if (ghostRow == null || ghostRow.length < n) { ghostRow = new float[n]; ghostFrom = new float[n]; }
        float[] row = ghostRow, off = ghostFrom;
        for (int y = y0; y <= y1; y++) {
            from.row(x0, y, n, row, 0);
            int sy = y - dy, sx = Math.max(0, x0 - dx), skipped = sx - (x0 - dx);
            if (sy >= 0 && skipped < n) {
                java.util.Arrays.fill(off, 0, n, from.fill);
                from.row(sx, sy, n - skipped, off, skipped);
                for (int i = 0; i < n; i++) { float v = (float) (GHOST * off[i]); if (v > row[i]) row[i] = v; }
            }
            g.setRow(x0, y, n, row, 0);
        }
        return g;
    }
    private static final double GHOST = PrintScreen.GHOST;

    /** Writes to {@code page} from now on: a smudge's render, once its tones composed off the page are written there. */
    void onto(Page page) { this.page = page; }
    /** Composes over {@code plane} (a smudging stroke's), from now on. */
    void carry(CarryPlane plane) { this.plane = plane; }
    /** Reads the page under a Behind or Invert stroke from {@code under}, from now on (null: none; the stroke drops it at finish). */
    void under(Under under) { this.under = under; }
    /**
     * Resist: composes wax's sheen ({@code on}) or the masking fluid from now on ({@link #sheen}); once anything has been
     * composed, every pixel the next time (what was shown was the other).
     */
    void sheen(boolean on) { sheen = on; if (shownFrom != null || mixed) release(); }
    /** Crayon: composes its wax onto the masking fluid from now on ({@link #waxMask}); every pixel, as {@link #sheen}. */
    void waxOnMask() { waxMask = true; if (shownFrom != null || mixed) release(); }
    /**
     * Filled Shape's fill ({@link ModularStroke#fillShape}): never darker than the darker of the tone found and the mark's
     * own over white, so where its outline already lies at its grey the fill adds nothing (the owner, 2026-10-23: "the
     * color is a bit diff for the stroke vs the fill"). Before anything is composed.
     */
    void keepDarker() { darker = true; }
    private boolean darker;

    /**
     * Lets go of what was shown over {@code rect} (page pixels): rows a fill composes once, for good ({@link
     * ModularStroke#fill}). A compose there again composes whatever is laid there anew.
     */
    void forget(int[] rect) {
        if (shownLine == null) return;
        shownLine.clear(rect); shownBody.clear(rect);
        if (shownRelief != null) shownRelief.clear(rect);
    }
    /** Drops what was shown (page- or lattice-sized buffers' worth of tiles): from now on every pixel is composed. The stroke calls it at finish. */
    void release() { mixed = true; shownLine = shownBody = shownRelief = null; shownFrom = null; }

    /**
     * A smudge with no paint of its own (no line, no body): only its plane changes what a compose writes, so a pixel
     * whose plane hasn't changed since it was last composed is left as it is, whatever the phase or buffers (a Drying
     * lift's, Clear Water's run-out): composing it again would write what is there.
     */
    private boolean smearsOnly() { return skipUnchanged && plane != null && !r.line && r.body == null; }
    /** Whether to compose only what changed since {@code body} was last composed in {@code phase}; false for good once another phase or body is read. */
    private boolean tracks(FloatTiles body, Phase phase) {
        if (!mixed) {
            if (shownFrom == null) { shownPhase = phase; shownFrom = body; }
            else if (phase != shownPhase || body != shownFrom) mixed = true;
        }
        // A print over prints reads its neighbours ({@link #edge}): every pixel composed again, so none keeps a neighbour's old reach.
        return skipUnchanged && !mixed && !r.replaces();
    }
    /**
     * Whether a pixel's (or lattice point's) body moving from {@code was} to {@code now} since last composed can change
     * what composing it writes. In a transparent Drying body's preview ({@code flat}: {@link #previewWash}, without a
     * smudge's plane) only whether the body lies there at all does: the preview's grey doesn't read how dark it is. Values
     * too small to stay above 0 when a lattice pixel averages them ({@link FloatTiles#between}) are compared exactly.
     */
    private static boolean bodyMoved(float now, float was, boolean flat) {
        if (now == was) return false;
        return !flat || (now > 0) != (was > 0) || Math.min(now, was) < TINY;
    }
    /** Below this a body value is compared exactly ({@link #bodyMoved}): a quarter of it is still a normal float above 0. */
    private static final float TINY = 4 * Float.MIN_NORMAL;
    /** Whether {@code phase}'s body is composed as a flat preview grey that only its presence decides ({@link #bodyMoved}). */
    private boolean flatPreview(Phase phase) { return phase == Phase.PREVIEW && !(r.cover > 0) && plane == null; }

    /**
     * Composes {@code rect}: reads strokeBaseTones, writes setTones (coverTones where cover lays paint); a pixel with
     * nothing whose tone differs from its base is restored. {@code l} is the line's coverage, {@code body} the body's
     * darkness, {@code relief} impasto's relief or null, all at every pixel.
     */
    void compose(FloatTiles line, FloatTiles bodies, FloatTiles relief, int[] rect, Phase phase) {
        if (ghosts != null) rect = misregistered(rect);
        FloatTiles l = ghosts != null ? ghost(line, rect) : line, body = ghosts != null ? ghost(bodies, rect) : bodies;
        int x0 = Math.max(0, rect[0]), y0 = Math.max(0, rect[1]), x1 = Math.min(width - 1, rect[2]), y1 = Math.min(height - 1, rect[3]);
        if (x1 < x0 || y1 < y0) return;
        nowLine = l; nowBody = body;
        boolean preview = phase == Phase.PREVIEW;
        FloatTiles rel = !preview && r.look.impasto > 0 ? relief : null;
        // Relief can change where the body hasn't, so impasto composes every pixel.
        boolean skip = (tracks(body, phase) || smearsOnly()) && rel == null && (shownLine == null || shownLine.shift == 0);
        // Once one compose takes every pixel, all later ones do (a mix is for good; relief, once laid, stays), so what was shown is dropped.
        if (!skip) release();
        else if (shownLine == null) { shownLine = new FloatTiles(width, height, 0); shownBody = new FloatTiles(width, height, 0); }
        FloatTiles shownL = shownLine, shownB = shownBody;
        boolean flat = flatPreview(phase);
        bands(y0, y1, (long)(x1 - x0 + 1) * (y1 - y0 + 1), (ya, yb, worker) -> {
            Rows w = rows(worker);
            long toned = 0;
            for (int y = ya; y <= yb; y++) {
                pageRow(w, l, body, rel, skip, flat, shownL, shownB, x0, x1, y);
                toned += tones(w, preview, rel != null, x0, x1, x0, x1, y);
            }
            Prof.count(Prof.TONED_PX, toned);
        });
    }

    /**
     * {@link #compose} for a big stroke's buffers on its lattice ({@link FloatTiles#shift} 1), each pixel read as
     * {@link FloatTiles#expand} stretches it, so it draws what stretching and then composing would. What changed is
     * found on the lattice: a pixel is composed only where a lattice point it reads changed since last composed (its
     * line, body or relief), a quarter of the comparing. {@code rect} must hold every page pixel those points reach.
     */
    void composeLattice(FloatTiles line, FloatTiles bodies, FloatTiles relief, int[] rect, Phase phase) {
        if (ghosts != null) rect = misregistered(rect);
        FloatTiles l = ghosts != null ? ghost(line, rect) : line, body = ghosts != null ? ghost(bodies, rect) : bodies;
        int x0 = Math.max(0, rect[0]), y0 = Math.max(0, rect[1]), x1 = Math.min(width - 1, rect[2]), y1 = Math.min(height - 1, rect[3]);
        if (x1 < x0 || y1 < y0) return;
        nowLine = l; nowBody = body;
        boolean preview = phase == Phase.PREVIEW;
        FloatTiles rel = !preview && r.look.impasto > 0 ? relief : null;
        // A smudge changes what lies under the stroke where line and body haven't: with a plane, a pixel is composed too
        // where the plane changed since (CarryPlane#takeFresh), every row and column of the rectangle looked at.
        boolean skip = (tracks(body, phase) || smearsOnly()) && (shownLine == null || shownLine.shift == 1);
        CarryPlane plane = this.plane;
        if (!skip) release();
        // The lattice points the rectangle's pixels read.
        int lw = l.latticeWidth, lh = l.latticeHeight;
        int m0 = x0 >> 1, m1 = Math.min(lw - 1, (x1 >> 1) + 1), n0 = y0 >> 1, n1 = Math.min(lh - 1, (y1 >> 1) + 1), mw = m1 - m0 + 1, mh = n1 - n0 + 1;
        if (skip) {
            if (shownLine == null) { shownLine = new FloatTiles(width, height, 0, 1); shownBody = new FloatTiles(width, height, 0, 1); }
            if (rel != null && shownRelief == null) shownRelief = new FloatTiles(width, height, 0, 1);
            if (changed == null || changed.length < mw * mh) changed = new byte[Math.max(mw * mh, 1 << 16)];
            if (changedFirst == null || changedFirst.length < mh) { changedFirst = new int[Math.max(mh, lh)]; changedLast = new int[Math.max(mh, lh)]; }
            FloatTiles sL = shownLine, sB = shownBody, sR = rel != null ? shownRelief : null;
            boolean flat = flatPreview(phase);
            // A point counts as shown only if every pixel it reaches (its own, and the ones between it and its
            // neighbours) is composed now; one reaching past the rectangle still marks its pixels inside.
            int[] inside = {firstWithin(x0, m0, m1), lastWithin(x1, width, m0, m1), firstWithin(y0, n0, n1), lastWithin(y1, height, n0, n1)};
            // First the lattice: what changed, and what was shown brought up to date, in bands of lattice tile rows.
            bands(n0, n1, (long)mw * mh * 4, (ya, yb, worker) -> {
                Rows w = rows(worker);
                for (int y = ya; y <= yb; y++)
                    changes(w, l, body, rel, sL, sB, sR, flat, m0, mw, y, (y - n0) * mw, y - n0, y >= inside[2] && y <= inside[3], inside[0] - m0, inside[1] - m0);
            });
        }
        int[] first = changedFirst, last = changedLast;
        byte[] marks = changed;
        bands(y0, y1, (long)(x1 - x0 + 1) * (y1 - y0 + 1), (ya, yb, worker) -> {
            Rows w = rows(worker);
            long toned = 0;
            for (int y = ya; y <= yb; y++) {
                int ly = y >> 1, lb = Math.min(lh - 1, ly + 1);
                boolean odd = (y & 1) != 0;
                // The row's pixels to compose lie within reach of its lattice rows' changes.
                int from = x0, to = x1;
                if (skip && plane == null) {
                    int a = first[ly - n0], b = last[ly - n0];
                    if (odd) { int fb = first[lb - n0], lbb = last[lb - n0]; if (fb >= 0 && (a < 0 || fb < a)) a = fb; if (lbb > b) b = lbb; }
                    if (a < 0) continue;
                    from = Math.max(x0, 2 * (m0 + a) - 1); to = Math.min(x1, 2 * (m0 + b) + 1);
                }
                l.row(m0, ly, mw, w.l0, 0); body.row(m0, ly, mw, w.b0, 0);
                if (odd) { l.row(m0, lb, mw, w.l1, 0); body.row(m0, lb, mw, w.b1, 0); }
                if (rel != null) { rel.row(m0, ly, mw, w.r0, 0); if (odd) rel.row(m0, lb, mw, w.r1, 0); }
                int base0 = (ly - n0) * mw, base1 = (lb - n0) * mw;
                for (int x = from; x <= to; x++) {
                    int i = (x >> 1) - m0, j = Math.min(mw - 1, i + 1), o = x - x0;
                    boolean oddX = (x & 1) != 0;
                    boolean want = !skip || marks[base0 + i] != 0 || oddX && marks[base0 + j] != 0
                            || odd && (marks[base1 + i] != 0 || oddX && marks[base1 + j] != 0) || plane != null && plane.has(x, y) && plane.takeFresh(x, y);
                    w.want[o] = want;
                    if (!want) continue;
                    w.c[o] = FloatTiles.between(w.l0[i], w.l0[j], odd ? w.l1[i] : 0, odd ? w.l1[j] : 0, oddX, odd);
                    w.d[o] = FloatTiles.between(w.b0[i], w.b0[j], odd ? w.b1[i] : 0, odd ? w.b1[j] : 0, oddX, odd);
                    if (rel != null) w.rel[o] = FloatTiles.between(w.r0[i], w.r0[j], odd ? w.r1[i] : 0, odd ? w.r1[j] : 0, oddX, odd);
                }
                toned += tones(w, preview, rel != null, x0, x1, from, to, y);
            }
            Prof.count(Prof.TONED_PX, toned);
        });
    }

    /** The first lattice point of {@code m0..m1} whose pixels (from {@code 2m − 1}, on the page) start at or after {@code p0}. */
    private static int firstWithin(int p0, int m0, int m1) {
        for (int m = m0; m <= m1; m++) if (Math.max(0, 2 * m - 1) >= p0) return m;
        return m1 + 1;
    }
    /** The last lattice point of {@code m0..m1} whose pixels (to {@code 2m + 1}, on a page {@code size} long) end at or before {@code p1}. */
    private static int lastWithin(int p1, int size, int m0, int m1) {
        for (int m = m1; m >= m0; m--) if (Math.min(size - 1, 2 * m + 1) <= p1) return m;
        return m0 - 1;
    }

    /**
     * Lattice row {@code y}'s {@code mw} points from column {@code m0}: which changed since last composed (marks from
     * {@code at} in {@link #changed}, the first and last at {@link #changedFirst}[{@code k}], −1 for none), and what
     * was shown set to them where {@code show}, at points {@code in0..in1} (from m0): those whose pixels are all composed now.
     * {@code flat}: a body counts as changed as {@link #bodyMoved} says.
     */
    private void changes(Rows w, FloatTiles l, FloatTiles body, FloatTiles rel, FloatTiles sL, FloatTiles sB, FloatTiles sR, boolean flat, int m0, int mw, int y, int at, int k,
                         boolean show, int in0, int in1) {
        l.row(m0, y, mw, w.l0, 0); body.row(m0, y, mw, w.b0, 0); sL.row(m0, y, mw, w.sl, 0); sB.row(m0, y, mw, w.sb, 0);
        if (sR != null) { rel.row(m0, y, mw, w.r0, 0); sR.row(m0, y, mw, w.sr, 0); }
        int first = -1, last = -1;
        for (int i = 0; i < mw; i++) {
            boolean moved = w.l0[i] != w.sl[i] || bodyMoved(w.b0[i], w.sb[i], flat) || sR != null && w.r0[i] != w.sr[i];
            changed[at + i] = (byte)(moved ? 1 : 0);
            if (moved) { if (first < 0) first = i; last = i; }
        }
        changedFirst[k] = first; changedLast[k] = last;
        int a = Math.max(first, in0), b = Math.min(last, in1);
        if (!show || first < 0 || b < a) return;
        sL.setRow(m0 + a, y, b - a + 1, w.l0, a); sB.setRow(m0 + a, y, b - a + 1, w.b0, a);
        if (sR != null) sR.setRow(m0 + a, y, b - a + 1, w.r0, a);
    }

    /**
     * Rows {@code y0..y1} in bands of {@link ToneDocument#TILE} rows (FloatTiles' tile rows too), so no two share a tile,
     * on several cores ({@link Cores#inBands}, {@link ToneDocument#openBands}).
     */
    private void bands(int y0, int y1, long area, Cores.Rows work) {
        if (y1 / ToneDocument.TILE == y0 / ToneDocument.TILE || area < Cores.SHARE_PX) { work.run(y0, y1, 0); return; }
        page.openBands();
        try { Cores.inBands(y0, y1, ToneDocument.TILE, area, work); }
        finally { page.closeBands(); }
    }

    /**
     * Page row {@code y}, columns {@code x0..x1}, of page-sized buffers into {@code w}'s line, body and relief, and
     * whether each pixel is composed: with {@code skip}, only one whose line or body differs from what was shown (and
     * what was shown set to it, its body as {@link #bodyMoved} says with {@code flat}); with nothing laid and nothing the
     * gesture changed, none.
     */
    private void pageRow(Rows w, FloatTiles l, FloatTiles body, FloatTiles rel, boolean skip, boolean flat, FloatTiles shownL, FloatTiles shownB, int x0, int x1, int y) {
        int ty = y >> FloatTiles.SHIFT, row = (y & FloatTiles.MASK) << FloatTiles.SHIFT;
        // A tile at a time: each buffer's tile is fetched once.
        for (int xs = x0; xs <= x1; ) {
            int tx = xs >> FloatTiles.SHIFT, xe = Math.min(x1, (tx << FloatTiles.SHIFT) | FloatTiles.MASK);
            float[] lt = l.tile(tx, ty, false), bt = body.tile(tx, ty, false);
            float[] slt = skip ? shownL.tile(tx, ty, false) : null, sbt = skip ? shownB.tile(tx, ty, false) : null;
            boolean smudged = plane != null && plane.has(xs, y);
            if (lt == null && bt == null && !smudged && (skip ? slt == null && sbt == null : !page.touched(xs, y))) {
                // Nothing laid here yet, or nothing laid and nothing the gesture changed: as the gesture found it.
                java.util.Arrays.fill(w.want, xs - x0, xe - x0 + 1, false);
                xs = xe + 1;
                continue;
            }
            for (int x = xs; x <= xe; x++) {
                int i = row | (x & FloatTiles.MASK), o = x - x0;
                float c = lt == null ? l.fill : lt[i], d = bt == null ? body.fill : bt[i];
                if (skip) {
                    float sc = slt == null ? 0 : slt[i], sd = sbt == null ? 0 : sbt[i];
                    // As last composed (or, with nothing, as the gesture found it).
                    // As last composed, and nothing smudged here since.
                    if (c == sc && !bodyMoved(d, sd, flat) && !(smudged && plane.takeFresh(x, y))) { w.want[o] = false; continue; }
                    if (c != sc) { if (slt == null) slt = shownL.tile(tx, ty, true); slt[i] = c; }
                    if (d != sd) { if (sbt == null) sbt = shownB.tile(tx, ty, true); sbt[i] = d; }
                }
                w.want[o] = true; w.c[o] = c; w.d[o] = d;
                if (rel != null) w.rel[o] = rel.get(x, y);
            }
            xs = xe + 1;
        }
    }

    /**
     * Writes page row {@code y}'s tones from {@code w}'s line, body and relief ({@code withRelief}), indexed from
     * {@code x0}, at the pixels marked wanted among {@code from..to} (within the rectangle {@code x0..x1}); the others
     * are left as they are. Returns how many pixels it wrote or passed over between those.
     */
    private int tones(Rows w, boolean preview, boolean withRelief, int x0, int x1, int from, int to, int y) {
        double cover = r.cover, paint = r.gray, tint = r.tint, washK = r.washK, inkK = r.inkK, bodyK = lerp(1, 0.4, clamp(r.look.impasto)), shade = r.previewShade;
        int[] base = w.under, tones = w.tones, coverages = w.coverages, found = w.found;
        // Erasable below 1: beside each tone, the stroke's own darkness (its tone over white paper), of which the
        // gesture's permanent share is what the eraser will leave (ToneDocument's write rule). At 1, none.
        // Blend: White and Invert lay coverage over what is under them (from the coverages found), and their writes keep
        // the share the eraser rule gives a pixel's change (owns null: the darkening since found, or what lightening took).
        ResolvedBrush.BlendMode mode = r.blendMode;
        boolean lays = mode.lays(), behind = mode == ResolvedBrush.BlendMode.BEHIND;
        Under under = this.under;
        int[] owns = r.erase < 1 && !lays ? w.owns : null, pageUnder = w.page;
        // The mark over white paper ({@code alone}): what Erasable keeps and what a Blend works from.
        // Screen: the mark's darkness over white through its threshold at each pixel.
        PrintScreen screen = r.screen;
        boolean waxMask = this.waxMask;
        // Light over dark (ResolvedBrush.laysLight): where the page under it is darker than its grey, the line (or the
        // screen's white dots) lays that grey, covering what is under it as opaque paint does, by the mark's reach.
        boolean light = under != null && r.laysLight();
        // White ink (ResolvedBrush.lightOver): a light pen's or marker's ink is that opaque over darker marks, its line
        // between darkening and covering, its body (a marker's flat ink, a pen's bleed) covering as opaque paint does.
        double lit = light ? r.lightOver() : 0;
        boolean litBody = light && screen == null && r.rule == ResolvedBrush.Rule.INK;
        // Filled Shape's fill (keepDarker): the darker of what is there and the mark alone.
        boolean keep = darker && mode == ResolvedBrush.BlendMode.OWN && !r.resist && !waxMask && screen == null;
        // A print over prints (ResolvedBrush.replaces): its own plane under its footprint is its pattern alone, lighter too.
        boolean replaces = r.replaces();
        boolean alone0 = owns != null || mode != ResolvedBrush.BlendMode.OWN || r.resist || waxMask || screen != null || keep, stick = r.rule == ResolvedBrush.Rule.CHARCOAL || r.rule == ResolvedBrush.Rule.CRAYON;
        // Screen's print tone (ResolvedBrush.prints): what the screened marks here would show unscreened, as the gesture
        // found it and as this stroke leaves it; the screen inks the second, so strokes over strokes print darker.
        int[] prints = r.prints() ? w.prints : null, printFound = w.printFound;
        // Resist: the share of it that stays (wax), which catches only the paper's high points (newmods.js composeStroke).
        double kept = 1 - r.erase, waxTooth = .25 + .5 * r.lineTooth;
        double lightShare = r.parts.inkShare * r.load;
        boolean[] wanted = w.want;
        float[] cs = w.c, ds = w.d, rels = w.rel;
        CarryPlane plane = this.plane;
        boolean covers = false;
        // run: the first pixel of a tail taken back still to restore; lo, hi: the row's first and last pixel to write (from x0).
        int run = -1, lo = -1, hi = -1;
        // A tile at a time: the tones the gesture found only where a pixel changes.
        for (int xs = from; xs <= to; ) {
            int xe = Math.min(to, (xs | FloatTiles.MASK));
            boolean based = false, smudged = plane != null && plane.has(xs, y);
            for (int x = xs; x <= xe; x++) {
                int o = x - x0;
                if (!wanted[o]) {
                    if (run >= 0) { page.restoreSpan(run, x, y); run = -1; }
                    continue;
                }
                float c = cs[o], d = ds[o];
                if (!based) {
                    // A smudging stroke reads the coverages found too: its write's floor where the plane isn't; White and
                    // Invert cover from them.
                    if (plane != null || lays || light) page.strokeBasePixels(xs, y, xe - xs + 1, 1, base, found);
                    else page.strokeBaseTones(xs, y, xe - xs + 1, 1, base);
                    // The page under a Behind or Invert stroke; without an Under, its own layer as found.
                    if (under != null) under.row(xs, xe - xs + 1, y, pageUnder);
                    else if (mode == ResolvedBrush.BlendMode.BEHIND || mode == ResolvedBrush.BlendMode.INVERT) System.arraycopy(base, 0, pageUnder, 0, xe - xs + 1);
                    if (prints != null) page.strokeBasePrints(xs, y, xe - xs + 1, printFound);
                    based = true;
                }
                int was = base[x - xs], u = was, floor = plane != null ? found[x - xs] : 0, held = lays || light ? found[x - xs] : 0;
                // What lies under the stroke: the page as found, or as smudged.
                if (smudged) { u = plane.tone(x, y); floor = held = plane.coverage(x, y); }
                // Pixels skipped since the last one written are left alone.
                if (lo < 0) lo = o;
                else for (int k = hi + 1; k < o; k++) { tones[k] = -1; coverages[k] = -1; if (owns != null) owns[k] = 0; if (prints != null) prints[k] = -1; }
                hi = o;
                if (owns != null) owns[o] = 0;
                // Where it lays nothing (a tail taken back), the print tone as found.
                if (prints != null) prints[o] = printFound[x - xs];
                // A smudge's floor; else no paint here (the page reads −1 as 0), and a body's pixel its coverage, 0 or
                // more, so tones composed ahead (StrokeTones) know which pixels laid paint.
                coverages[o] = plane != null ? floor : -1;
                if (c <= 0 && d <= 0) {
                    if (u != was || plane != null && floor != found[x - xs]) { tones[o] = u; if (run >= 0) { page.restoreSpan(run, x, y); run = -1; } continue; }
                    tones[o] = -1;
                    // A tail taken back: the run goes back to what the gesture found.
                    if (page.differs(x, y, was)) { if (run < 0) run = x; }
                    else if (run >= 0) { page.restoreSpan(run, x, y); run = -1; }
                    continue;
                }
                if (run >= 0) { page.restoreSpan(run, x, y); run = -1; }
                // Opaque Lift: its paint greys toward darker paint it covers.
                double gray = tint > 0 && u < paint ? paint + (u - paint) * tint : paint;
                // Erasable: the same paint over white paper (white: 255), step for step, so a mark laid alone and the
                // same mark's own coverage over other paint agree to the grey.
                double tone = u, alone = 255, toward = 0;
                if (d > 0) {
                    double reach, cov = litBody && paint > pageUnder[x - xs] ? lit : cover;
                    if (preview) {
                        double t = previewWash(u, shade);
                        reach = Math.min(1, d);
                        tone = cov > 0 ? lerp(t, lerp(u, gray, reach), cov) : t;
                        if (alone0) { double t1 = previewWash(255, shade); alone = cov > 0 ? lerp(t1, lerp(255, paint, reach), cov) : t1; }
                    } else {
                        double dd = Math.min(1.6, d), body = 1.0 - (float)ModularSizes.laid(dd * washK), glaze = u * body;
                        reach = Math.min(1, dd / bodyK);
                        tone = cov > 0 ? lerp(glaze, lerp(u, gray, reach), cov) : glaze;
                        if (alone0) alone = cov > 0 ? lerp(255 * body, lerp(255, paint, reach), cov) : 255 * body;
                        if (withRelief) { float rv = rels[o]; if (rv != 0) { tone = clamp(tone + rv, 0, 255); alone = clamp(alone + rv, 0, 255); } }
                    }
                    if (cov > 0) { coverages[o] = Math.max(coverages[o], (int)Math.round(255 * cov * reach * (behind ? blank(pageUnder[x - xs]) : 1))); covers = true; }
                }
                if (c > 0) {
                    // Screened: the line's own darkness, its footprint even (ResolvedBrush: no stick grain or tooth to break it).
                    double a = screen != null ? c : ink(c, u, x, y);
                    // White and Invert work from the mark as it lays on blank paper (newmods.js): a charcoal stick's
                    // contact depends on what is under it, so its line is worked out over white for them.
                    // So is a crayon's wax on the masking fluid (the tone under it there is the fluid's).
                    double onPaper = (lays || waxMask || keep) && stick ? ink(c, 255, x, y) : a;
                    if (light && screen == null && paint > pageUnder[x - xs]) {
                        // A light stick or pencil over darker marks: its grey over them by its reach, its tooth and grain kept.
                        double k = Math.min(1, a * lightShare);
                        if (k > 0 && lit < 1) {
                            // Light grey ink, short of white: between its darkening and its cover, by how opaque it is.
                            double dark = tone * (1.0 - (float)(a * inkK)), darkAlone = alone * (1.0 - (float)(onPaper * inkK));
                            tone = dark + (tone + (paint - tone) * k - dark) * lit; alone = darkAlone + (255 - (255 - paint) * k - darkAlone) * lit;
                            coverages[o] = Math.max(coverages[o], (int)Math.round(held + (255 - held) * k * lit)); covers = true;
                        } else if (k > 0) {
                            tone += (paint - tone) * k; alone = 255 - (255 - paint) * k;
                            coverages[o] = Math.max(coverages[o], (int)Math.round(held + (255 - held) * k)); covers = true;
                        }
                    } else {
                        if (a > 0) { float k = (float)(a * inkK); tone = tone * (1.0 - k); }
                        if (onPaper > 0) alone = alone * (1.0 - (float)(onPaper * inkK));
                    }
                }
                if (r.resist) {
                    // The mark's darkness over white, a; wax's share of it (the eraser can't lift it) only on the
                    // paper's high points, as wax skips the grain's valleys. Onto the mask: as strong as the fluid there
                    // or a, whichever is more. Onto the ink layer: wax's sheen, a grey step at most.
                    double a = 1 - Math.max(0, Math.min(255, alone)) / 255;
                    if (r.lineTooth > 0 && kept > 0) a *= 1 - kept + kept * smoothstep(waxTooth - .1, waxTooth + .1, height(x, y));
                    double k = sheen ? 1 - WAX_SHEEN * a * kept : 1 - a;
                    tone = sheen ? u * k : Math.min(u, 255 * k); alone = 255 * k;
                } else if (waxMask) {
                    // Crayon's wax onto the mask: as strong as the fluid there or the wax, whichever is more.
                    double k = 1 - Crayon.mask(1 - Math.max(0, Math.min(255, alone)) / 255, r.wax);
                    tone = Math.min(u, 255 * k); alone = 255 * k;
                } else if (mode != ResolvedBrush.BlendMode.OWN) {
                    if (behind) {
                        // Only where the page is still blank: the mark as the medium lays it, by how blank it is there.
                        double f = blank(pageUnder[x - xs]);
                        tone = u + (tone - u) * f; alone = 255 - (255 - alone) * f;
                    } else {
                        // White and Invert: the mark's own darkness over white, a, moves what is under it toward white
                        // (or its opposite) as paint of that tone laid over it at a would, covering it by a.
                        double a = 1 - Math.max(0, Math.min(255, alone)) / 255;
                        toward = mode == ResolvedBrush.BlendMode.WHITE ? 255 : 255 - pageUnder[x - xs];
                        tone = u + (toward - u) * a;
                        coverages[o] = (int)Math.round(held + (255 - held) * a);
                        covers = true;
                    }
                }
                if (screen != null) {
                    // Screen (the mischief modules spec): the threshold here, fixed to the page, inks the tone the mark
                    // would lay unscreened: its own darkness over white built on the print tone found (PrintScreen.printed;
                    // White, Invert and Tone over white alone). Ink: black (White's white, Invert's opposite, Tone's dot);
                    // none: opaque paint lays white by its coverage, any other mark nothing, the page as the gesture found it.
                    // Its darkness: the stroke's own tone wherever its tip lies (ResolvedBrush.screenTone), not the grey
                    // the mark would leave, which fell off toward its edge.
                    double a = r.screenTone(c, d);
                    if (prints != null) {
                        int v = printFound[x - xs];
                        double p;
                        if (replaces) {
                            // Its tone replaces the print tone found, but at its anti-aliased edge, which keeps that much of
                            // what was there: a stroke over one of the same tone prints the same dots, no seam along its edge.
                            double full = edge(x, y, c, d);
                            p = v + (255 * (1 - full) - v) * (full > 0 ? a / full : 1);
                        } else p = PrintScreen.printed(v, a, cover > 0 ? Math.max(0, coverages[o]) / 255.0 : 0);
                        prints[o] = (int) Math.max(0, Math.min(255, Math.round(p)));
                        // Inked by the print tone as kept, to the grey step, so a second stroke at the same tone (the
                        // darker of the two, kept) inks exactly the dots the first did.
                        a = 1 - prints[o] / 255.0;
                    }
                    if (screen.ink(a, x, y, height)) {
                        if (screen.tone) { tone = Math.min(u, PrintScreen.DOT_TONE); coverages[o] = PrintScreen.DOT_COVERAGE; covers = true; }
                        else if (lays) { tone = toward; coverages[o] = 255; }
                        else tone = 0;
                        alone = 0;
                    } else if (replaces && (!screen.tone || a >= .95 * edge(x, y, c, d))) {
                        // Between its dots: clear, so a lighter print over a darker one leaves the lighter one, the marks
                        // under its plane showing. Tone keeps no print tone: at its anti-aliased edge its plane stays as found.
                        tone = 255; coverages[o] = 0; alone = 255;
                    } else if (light && paint > pageUnder[x - xs] && screen.white((paint - pageUnder[x - xs]) / (255.0 - pageUnder[x - xs]) * r.screenReach(c, d), x, y)) {
                        // A light print over darker marks (ResolvedBrush.laysLight): white where the screen's thresholds are
                        // highest, its dots' complement, the share that leaves the page at the print's grey on average,
                        // crisp as its black dots; its black dots, and any print's under it, ink first.
                        tone = 255; coverages[o] = 255; covers = true; alone = 255;
                    } else if (cover > 0 && !lays) {
                        tone = u + (255 - u) * Math.max(0, coverages[o]) / 255.0; alone = 255;
                    } else {
                        // As where nothing is laid: the smudge's, or as the gesture found it.
                        coverages[o] = plane != null ? floor : -1;
                        if (u != was || plane != null && floor != found[x - xs]) { tones[o] = u; continue; }
                        tones[o] = -1;
                        if (page.differs(x, y, was)) run = x;
                        continue;
                    }
                }
                if (keep) tone = Math.max(tone, Math.min(u, alone));
                tones[o] = (int)Math.max(0, Math.min(255, Math.round(tone)));
                if (owns != null) owns[o] = 255 - (int)Math.max(0, Math.min(255, Math.round(alone)));
            }
            xs = xe + 1;
        }
        if (run >= 0) page.restoreSpan(run, to + 1, y);
        if (lo < 0) return 0;
        int n = hi - lo + 1;
        if (lo > 0) { System.arraycopy(tones, lo, tones, 0, n); System.arraycopy(coverages, lo, coverages, 0, n); if (owns != null) System.arraycopy(owns, lo, owns, 0, n); }
        if (plane != null || replaces) page.smearTones(x0 + lo, y, n, 1, tones, coverages, owns);
        else if (covers) page.coverTones(x0 + lo, y, n, 1, tones, coverages, owns);
        else page.setTones(x0 + lo, y, n, 1, tones, owns);
        if (prints != null) { if (lo > 0) System.arraycopy(prints, lo, prints, 0, n); page.setPrints(x0 + lo, y, n, prints); }
        return n;
    }

    /**
     * A screened mark's darkness at page pixel {@code x, y} as if wholly under its tip ({@link ResolvedBrush#screenTone}
     * of the strongest line {@code c} and body {@code d} there and at its four neighbours): over it, the pixel's own is
     * the share its anti-aliased edge lays.
     */
    private double edge(int x, int y, float c, float d) {
        FloatTiles l = nowLine, b = nowBody;
        for (int k = 0; k < 4; k++) {
            int nx = x + (k == 0 ? -1 : k == 1 ? 1 : 0), ny = y + (k == 2 ? -1 : k == 3 ? 1 : 0);
            if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
            c = Math.max(c, l.get(nx, ny)); d = Math.max(d, b.get(nx, ny));
        }
        return r.screenTone(c, d);
    }

    /** How much of a stick's line (charcoal, pastels, soft graphite) its grain and soft edge leave inked, about: its quick preview's ink. */
    static final double STICK_SHARE = .6;
    /**
     * A Drying body's tone over {@code u} while the pen is down (PREVIEW): a light, even grey by the swatch's {@code shade}
     * ({@link ResolvedBrush#previewShade}). A wash darker than the old black (shade above 1) darkens it further by the
     * same steps over white (55 greys a shade), in proportion over a darker {@code u}, so it never lightens over darker
     * paint.
     */
    static double previewWash(double u, double shade) {
        return shade <= 1 ? u - (u - Math.min(200, u * 0.82)) * shade : Math.min(200, u * 0.82) * (1 - PREVIEW_PAST * (shade - 1));
    }
    /** {@link #previewWash}'s darkening past shade 1, a share of its grey a shade: over white 55 greys of 200. */
    static final double PREVIEW_PAST = 55 / 200.0;
    /**
     * A quick stroke's flat tone over {@code u} ({@link QuickStroke}): where its {@code body} lies, a wash as Drying's
     * preview shows it ({@link #previewWash}), or opaque paint's own grey; where its {@code line} lies, the line's ink on
     * top, at full coverage (a stick's at {@link #STICK_SHARE}); a smudge with Own paint under .5, the light wash; Resist
     * its fluid on the mask, or wax's sheen. No
     * texture, no paper. White and Invert: {@code u} is the page under the mark ({@link Under}), moved toward white or
     * its opposite by the flat mark's darkness over white (Behind is the medium's own tone, where the page is blank).
     */
    static int flatTone(ResolvedBrush r, int u, boolean line, boolean body) {
        // Resist: masking fluid flat onto the mask (u the mask's tone), as strong as the shade; wax its sheen.
        if (r.resist) return (int)Math.round(r.erase < 1 ? u * (1 - WAX_SHEEN * r.inkK * (1 - r.erase)) : Math.min(u, 255 * (1 - r.inkK)));
        if (r.blendMode.lays()) {
            double a = 1 - ownFlat(r, 255, line, body) / 255.0, toward = r.blendMode == ResolvedBrush.BlendMode.WHITE ? 255 : 255 - u;
            return (int)Math.max(0, Math.min(255, Math.round(u + (toward - u) * a)));
        }
        return ownFlat(r, u, line, body);
    }
    /** {@link #flatTone} as the medium lays it. */
    static int ownFlat(ResolvedBrush r, int u, boolean line, boolean body) {
        // A smudge with little paint of its own shows where it goes as the light wash: something happens here.
        if (r.carry != null && r.carry.moves && r.load < .5) return (int)Math.round(previewWash(u, r.previewShade));
        double tone = u;
        // White ink (ResolvedBrush.lightOver): a light pen's or marker's ink over darker marks, that opaque.
        double lit = r.lightOver(), cover = r.cover > 0 ? r.cover : lit > 0 && r.screen == null && r.rule == ResolvedBrush.Rule.INK && r.gray > u ? lit : 0;
        if (body) { double t = previewWash(u, r.previewShade); tone = cover > 0 ? lerp(t, r.gray, cover) : t; }
        double share = r.stick() ? STICK_SHARE : 1;
        if (line && lit > 0 && r.gray > tone) {
            double covered = tone + (r.gray - tone) * Math.min(1, r.parts.inkShare * r.load * share), dark = tone * (1.0 - (float)(r.inkK * share));
            tone = lit < 1 ? dark + (covered - dark) * lit : covered;
        }
        else if (line) tone = tone * (1.0 - (float)(r.inkK * share));
        return (int)Math.max(0, Math.min(255, Math.round(tone)));
    }

    /**
     * The line's ink at {@code x, y} from its coverage {@code c} over tone {@code u}, by the medium's rule. Apart from
     * {@link #tones}, a method small enough for the device's compiler to fold its noises and steps into.
     */
    private double ink(double c, double u, int x, int y) {
        double tooth = r.lineTooth, spread = r.spread;
        ResolvedBrush.Rule rule = r.rule;
        if (rule == ResolvedBrush.Rule.INK) return tooth > 0 ? clamp((c - (0.5 + tooth * 0.9 * (height(x, y) - 0.5))) * 4 + 0.5) : c;
        if (rule == ResolvedBrush.Rule.CRAYON) {
            // Crayon: wax on the tooth (the paper's too, by its Texture), lifted by what is already there; Scratch, its blade.
            if (r.scratch) return c * Crayon.scrape(x, y);
            return Crayon.wax(c, lerp(Crayon.tooth(x, y), height(x, y), .3 * tooth), 1 - u / 255, r.wax);
        }
        if (rule == ResolvedBrush.Rule.CHARCOAL) {
            // The contact rule: the stick touches the high points first; pressure, a firmer binder (Hold) and
            // charcoal already there reach into the valleys. The stick's own grain, the paper's by its tooth.
            return contact.ink(c, u, grain.fine(x, y) * .65 + grain.mid(x, y) * .35, height(x, y));
        }
        // Graphite, with no Fill plane: the tooth holds nothing yet.
        double grade = r.grade;
        double pp = .5 + (height(x, y) - .5) * (.35 + .65 * tooth);
        double depth = clamp(c * (.9 - .35 * grade)), soft = lerp(.12, .04, grade);
        return c * smoothstep(1 - depth - soft, 1 - depth + soft, pp) + c * .15 * spread;
    }

    /**
     * Charcoal's contact rule ({@link #ink}), its brush's terms worked out once: the stick touches the high points first;
     * pressure, a firmer binder (Hold) and charcoal already there reach into the valleys. The quick preview reads it too
     * ({@link QuickStroke}), so its grain lies where the final's does.
     */
    static final class Contact {
        private final double mix, reach, under, soft, spread, across;
        Contact(ResolvedBrush r) {
            mix = .3 + .4 * r.lineTooth; reach = lerp(.62, .97, r.hold); under = .4 + .6 * r.hold; soft = .07 + .22 * r.spread; spread = r.spread; across = 1 / (2 * soft);
        }
        /** The ink of coverage {@code c} over tone {@code u}, where the stick's grain is {@code stick} and the paper's height {@code pp} (both 0 to 1). */
        double ink(double c, double u, double stick, double pp) {
            double hgt = height(stick, pp), ex = 1 - u / 255;
            double depth = clamp(c * reach + .5 * ex * under);
            return Math.max(c * smoothstep(1 - depth - soft, 1 - depth + soft, hgt), c * .5 * spread);
        }
        /** The height the rule reads where the stick's grain is {@code stick} and the paper's height {@code pp} (the quick preview's). */
        double height(double stick, double pp) { return lerp(stick, pp, mix); }
        /** {@link #ink} for the quick preview, the height worked out ({@link #height}), without its divisions ({@code ex}, the darkness found, 1 − u / 255). */
        double preview(double c, double ex, double hgt) {
            double depth = c * reach + .5 * ex * under;
            double s = (hgt - 1 + (depth > 1 ? 1 : depth) + soft) * across;
            s = s <= 0 ? 0 : s >= 1 ? 1 : s;
            return Math.max(c * s * s * (3 - 2 * s), c * .5 * spread);
        }
    }

    /** The paper's height at {@code x, y}, 0 to 1: its fine and mid noises, as the line rules read it. */
    private double height(int x, int y) { return paper.fine(x, y) * .65 + paper.mid(x, y) * .35; }
}
