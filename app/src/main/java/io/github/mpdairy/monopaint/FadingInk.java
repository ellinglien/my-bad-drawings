package io.github.mpdairy.monopaint;

/**
 * Fading and Lemon Juice (the mischief modules spec, Stage 3; {@code mischief-lab.html}'s {@code dab3} and
 * {@code shade3}): Drying choices whose mark changes by the clock after it is laid. Fading fades away unevenly, thin
 * parts and the paper's damp patches first, until nothing of it is left on the page or in the file; Lemon Juice goes on
 * as a faint trace and darkens in unevenly over its drying time, then is an ordinary dried mark.
 *
 * <p>A mark ({@link Mark}) is its stroke's Undo step as laid: the tiles it changed, as they were before it (what it
 * fades back to) and as it laid them (what lemon juice darkens to), with when it was laid. Each pixel shows a share of
 * that change worked out from its age (its fade weight: how much the stroke changed it; and a mottle fixed to the page),
 * stepped as coarsely as e-ink looks fine ({@link #stepMs}). Only the clock decides, so a page closed mid-fade keeps
 * fading when it is opened again. The page writes the shares ({@link ToneDocument#fade}); here is what they are. Pure,
 * checked on the desktop ({@code FadingChecks}).
 */
final class FadingInk {
    private FadingInk() {}

    /** What a brush with Fading or Lemon Juice lays: which, and its time (Fades In, Dries In), ms. */
    static final class Spec {
        final boolean lemon;
        final int ms;
        Spec(boolean lemon, int ms) { this.lemon = lemon; this.ms = Math.max(1, ms); }
    }

    /** The page's clock, wall time in ms: the same after the app is closed and opened again. The checks set their own. */
    static java.util.function.LongSupplier clock = System::currentTimeMillis;
    static long now() { return clock.getAsLong(); }

    /** The longest Fades In and Dries In a file may hold, ms. */
    static final int MOST_MS = 10 * 60 * 1000;
    /** Lemon juice goes on at this share of its darkness, of 255 (about 10%, the prototype's). */
    static final int TRACE = 26;

    /**
     * One mark changing by the clock, immutable ({@link #at}): the layer it is on and the page's size when it was laid
     * (its tiles' keys are that page's, {@link ToneDocument#TILE} square); Lemon Juice or Fading, and its time; each
     * tile it changed as it was before ({@code before}) and as laid ({@code after}), tones then coverages as an Undo
     * step's tiles are; when it was laid, and the clock time its pixels show ({@code shown}; -1: as laid, before any).
     */
    static final class Mark {
        final ToneDocument.Layer layer;
        final int width, height, ms;
        final boolean lemon;
        final long born, shown;
        final int[] keys;
        final byte[][] before, after;
        Mark(ToneDocument.Layer layer, int width, int height, boolean lemon, int ms, long born, long shown, int[] keys, byte[][] before, byte[][] after) {
            this.layer = layer; this.width = width; this.height = height; this.lemon = lemon; this.ms = ms; this.born = born; this.shown = shown;
            this.keys = keys; this.before = before; this.after = after;
        }
        /** This mark with its pixels showing clock time {@code t}. */
        Mark at(long t) { return new Mark(layer, width, height, lemon, ms, born, t, keys, before, after); }
        /** This mark on {@code other} (a copy of its layer). */
        Mark on(ToneDocument.Layer other) { return new Mark(other, width, height, lemon, ms, born, shown, keys, before, after); }
        /** When every pixel is done: lemon juice fully dark, a fading mark gone. */
        long end() { return born + (long) Math.ceil(ms * (lemon ? LEMON_MOST : FADE_MOST)); }
        /** The next clock time its pixels should show, at most its end. */
        long next() { return shown < 0 ? born : Math.min(end(), shown + stepMs(lemon, ms)); }
        /** The bytes its tiles hold. */
        long bytes() { long n = 0; for (int i = 0; i < keys.length; i++) n += before[i].length + after[i].length; return n; }
        int columns() { return (width + ToneDocument.TILE - 1) / ToneDocument.TILE; }
        /** The share, 0 to 255, of its change pixel {@code p} of tile {@code j} shows at clock time {@code t}; -1 is as laid. */
        int share(int j, int p, long t) {
            if (t < 0) return 255;
            int cols = columns(), x0 = keys[j] % cols * ToneDocument.TILE, y0 = keys[j] / cols * ToneDocument.TILE, w = Math.min(ToneDocument.TILE, width - x0);
            byte[] b = before[j], a = after[j];
            int n = b.length / 2;
            int dark = Math.abs(onWhite(a[p] & 255, a[n + p] & 255) - onWhite(b[p] & 255, b[n + p] & 255));
            return FadingInk.share(lemon, ms, dark, mottle(x0 + p % w, y0 + p / w), t - born);
        }
    }

    /** A fading pixel's life is at most this many times Fades In (its darkest, slowest mottle), lemon juice's Dries In. */
    static final double FADE_MOST = 1.25, LEMON_MOST = 1.3;

    /**
     * The share, 0 to 255, of a mark's change a pixel shows {@code age} ms after it was laid (the prototype's
     * {@code shade3}), {@code dark} how much the stroke changed it (0 to 255) and {@code mottle} the page's at it. Fading:
     * its life is Fades In times .35 to 1 by its darkness (thin parts go first) and .75 to 1.25 by the mottle; it holds,
     * then thins out over the last 45% of it to nothing. Lemon Juice: from {@link #TRACE} to all of it over Dries In
     * times .7 to 1.3 by the mottle, eased.
     */
    static int share(boolean lemon, int ms, int dark, int mottle, long age) {
        float m = mottle / 255f;
        if (lemon) {
            float u = Math.max(0, Math.min(1, age / (ms * (.7f + .6f * m))));
            return u >= 1 ? 255 : TRACE + Math.round((255 - TRACE) * u * u * (3 - 2 * u));
        }
        float life = ms * (.35f + .65f * dark / 255f) * (.75f + .5f * m), left = 1 - age / life;
        return left <= 0 ? 0 : Math.round(255 * Math.min(1, left * 2.2f));
    }

    /**
     * How often its tones step, ms: as coarsely as e-ink looks fine, a partial refresh each (the spec's; the prototype's
     * four a second is too many for the panel). Lemon Juice a twentieth of Dries In, 1 to 15 s; Fading a tenth of Fades In, 2 to 5 s.
     */
    static long stepMs(boolean lemon, int ms) { return lemon ? Math.max(1000, Math.min(15000, ms / 20)) : Math.max(2000, Math.min(5000, ms / 10)); }

    /**
     * The mottle fixed to the page at pixel {@code x, y}, 0 to 255 (the prototype's {@code mott}: soft value noise and a
     * pixel of grit, .7 and .3): the paper's 16 px blob ({@link PaperNoise#blob}), about the prototype's 7 CSS px.
     */
    static int mottle(int x, int y) {
        int h = x * 0x27d4eb2d ^ y * 0x165667b1 ^ 0x2b;
        h = (h ^ h >>> 15) * 0x85ebca6b; h = (h ^ h >>> 13) * 0xc2b2ae35; h ^= h >>> 16;
        return (7 * PaperNoise.blob(x, y) + 3 * (h & 255) + 5) / 10;
    }

    /**
     * A pixel {@code share} of the way from {@code t0, a0} (before) to {@code t1, a1} (as laid), a layer's tone and
     * coverage: coverage and the paint it holds (darkness times coverage) each that share of the way, so the pixel over
     * white is too. Exactly the one at 0 and the other at 255. Tone in the high byte, coverage in the low.
     */
    static int mix(int t0, int a0, int t1, int a1, int share) {
        if (share <= 0) return t0 << 8 | a0;
        if (share >= 255) return t1 << 8 | a1;
        int a = a0 + div255((a1 - a0) * share);
        if (a <= 0) return 255 << 8;
        int p0 = (255 - t0) * a0, p1 = (255 - t1) * a1, p = p0 + div255((p1 - p0) * share);
        int t = 255 - (p + a / 2) / a;
        return Math.max(0, Math.min(255, t)) << 8 | a;
    }
    private static int div255(int v) { return v >= 0 ? (v + 127) / 255 : -((-v + 127) / 255); }

    /** The most marks a page's file holds. */
    static final int MOST_MARKS = 4096;
    /**
     * A file's FADE section ({@link DocumentCodec}): how many marks, then each one's layer (its index in {@code layers}),
     * kind (1 Lemon Juice, 0 Fading), time (ms), when it was laid and the clock time its pixels show (wall time, ms), how
     * many tiles, and each tile's key and its pixels before and as laid (tones then coverages each), as a page's are.
     * Marks on a layer not in {@code layers} are left out. Inkwash: a mark on a kind plane names it by its layer and
     * slot ({@link ToneDocument#address}: {@code layer | slot << 4}).
     */
    static byte[] encode(java.util.List<Mark> marks, java.util.List<ToneDocument.Layer> layers) throws java.io.IOException {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        java.io.DataOutputStream out = new java.io.DataOutputStream(bytes);
        int count = 0;
        for (Mark m : marks) if (ToneDocument.address(layers, m.layer) >= 0) count++;
        out.writeInt(count);
        for (Mark m : marks) {
            int layer = ToneDocument.address(layers, m.layer);
            if (layer < 0) continue;
            out.writeByte(layer); out.writeByte(m.lemon ? 1 : 0); out.writeInt(m.ms); out.writeLong(m.born); out.writeLong(m.shown);
            out.writeInt(m.keys.length);
            for (int j = 0; j < m.keys.length; j++) { out.writeInt(m.keys[j]); out.write(m.before[j]); out.write(m.after[j]); }
        }
        out.flush();
        return bytes.toByteArray();
    }
    /** {@link #encode}'s marks on a {@code width × height} page whose layers are {@code layers}; refuses a section that isn't one. */
    static java.util.List<Mark> decode(byte[] section, int width, int height, java.util.List<ToneDocument.Layer> layers) throws java.io.IOException {
        java.io.DataInputStream in = new java.io.DataInputStream(new java.io.ByteArrayInputStream(section));
        java.util.List<Mark> marks = new java.util.ArrayList<>();
        int tile = ToneDocument.TILE, cols = (width + tile - 1) / tile, rows = (height + tile - 1) / tile;
        try {
            int count = in.readInt();
            if (count < 0 || count > MOST_MARKS) throw new java.io.IOException("Invalid fading section");
            for (int i = 0; i < count; i++) {
                int layer = in.readUnsignedByte(), lemon = in.readUnsignedByte(), ms = in.readInt();
                long born = in.readLong(), shown = in.readLong();
                int n = in.readInt();
                ToneDocument.Layer on = ToneDocument.at(layers, layer);
                if (on == null || lemon > 1 || ms < 1 || ms > MOST_MS || shown < -1 || n < 1 || n > cols * rows) throw new java.io.IOException("Invalid fading section");
                int[] keys = new int[n];
                byte[][] before = new byte[n][], after = new byte[n][];
                java.util.HashSet<Integer> seen = new java.util.HashSet<>();
                for (int j = 0; j < n; j++) {
                    int key = in.readInt();
                    if (key < 0 || key >= cols * rows || !seen.add(key)) throw new java.io.IOException("Invalid fading section");
                    int x0 = key % cols * tile, y0 = key / cols * tile, size = 2 * Math.min(tile, width - x0) * Math.min(tile, height - y0);
                    keys[j] = key; before[j] = new byte[size]; after[j] = new byte[size];
                    in.readFully(before[j]); in.readFully(after[j]);
                }
                marks.add(new Mark(on, width, height, lemon == 1, ms, born, shown, keys, before, after));
            }
            if (in.read() != -1) throw new java.io.IOException("Invalid fading section");
        } catch (java.io.EOFException short_) { throw new java.io.IOException("Short fading section"); }
        return marks;
    }
    static int onWhite(int tone, int alpha) { return (tone * alpha + 255 * (255 - alpha) + 127) / 255; }
}
