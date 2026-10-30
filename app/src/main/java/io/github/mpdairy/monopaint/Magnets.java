package io.github.mpdairy.monopaint;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A page's fridge magnets (the owner's, 2026-10-18; Paintmageddon's magnetic poetry): word tiles lying over the drawing,
 * never in its layers. The first is the bottom one, the last on top. Immutable: every change makes another list, so an
 * Undo step holds the magnets before and after by reference ({@link ToneDocument#setMagnets}) and a file keeps them in
 * TSM5's WORD section ({@link DocumentCodec}, {@link #encode}). The tile's size and shape are here too, the same on the
 * page, in the bin ({@link WordBinLayout}) and in a PNG copy ({@link #stamp}): page pixels, which are the panel's (300
 * ppi on both tablets, 1.875 a dp). A file keeps each magnet's word, centre, tilt and turn, never its size, so a page
 * saved with the first, bigger tiles (2026-10-18) opens with these, each centred where it was. Pure, checked on the
 * desktop ({@code MagnetChecks}).
 */
final class Magnets {
    /** One magnet: its word, its tile's centre on the page (pixels), its tilt in tenths of a degree, its quarter turns (the app's turn when it was placed, so it reads upright). */
    static final class Magnet {
        final String word;
        final int x, y, tilt, turn;
        Magnet(String word, int x, int y, int tilt, int turn) { this.word = word; this.x = x; this.y = y; this.tilt = tilt; this.turn = turn & 3; }
        /** The same magnet with its centre at {@code x, y}. */
        Magnet at(int x, int y) { return new Magnet(word, x, y, tilt, turn); }
        @Override public boolean equals(Object o) {
            if (!(o instanceof Magnet)) return false;
            Magnet m = (Magnet) o;
            return m.x == x && m.y == y && m.tilt == tilt && m.turn == turn && m.word.equals(word);
        }
        @Override public int hashCode() { return ((word.hashCode() * 31 + x) * 31 + y) * 31 + tilt * 4 + turn; }
        @Override public String toString() { return word + "@" + x + "," + y; }
    }

    /** Pixels a millimetre on the page (the panel's 300 ppi). */
    static final float MM = 300 / 25.4f;
    /**
     * The word's type size: 2.7 mm, Roboto Mono Bold (Paintmageddon's tiles: 13 px words with 3 × 7 px room, scaled to
     * read like real fridge magnets; res/font/roboto_mono_bold.ttf, the magnets' face only).
     */
    static final int EM = 32;
    /** A tile's height, 4.7 mm (the word's line, 1.32 em, and 7 px above and below it), and its least width (a square: "s" and "a" too). */
    static final int HEIGHT = 56;
    /** Where the text's baseline is, from the tile's top: the capitals and the baseline about centred. */
    static final int BASELINE = 40;
    /** The fine grey edge round the tile's face, and the room between it and the word, each side. */
    static final int LINE = 1, PAD = 17;
    /** The crisp ink "shadow": the tile again, solid ink, 2 px down and right of it (never blurred). */
    static final int SHADOW = 2;
    /** The tile's corners: nearly square. */
    static final float CORNER = 2;
    /** Antialiased edges' room round a tilted tile. */
    static final int EDGE = 2;
    /** The tilt a placed magnet gets at random, either way, tenths of a degree (Paintmageddon's ±2.5°). */
    static final int MOST_TILT = 25;
    /** The longest word a tile holds, and the most magnets on a page. */
    static final int MOST_LETTERS = 24, MOST = 400;

    static final Magnets NONE = new Magnets(Collections.<Magnet>emptyList());
    private final List<Magnet> list;
    private Magnets(List<Magnet> list) { this.list = list; }
    static Magnets of(List<Magnet> magnets) { return magnets.isEmpty() ? NONE : new Magnets(Collections.unmodifiableList(new ArrayList<>(magnets))); }

    int size() { return list.size(); }
    boolean isEmpty() { return list.isEmpty(); }
    Magnet get(int i) { return list.get(i); }
    List<Magnet> list() { return list; }
    /** With {@code m} on top; these when the page holds {@link #MOST} already. */
    Magnets with(Magnet m) {
        if (list.size() >= MOST) return this;
        ArrayList<Magnet> next = new ArrayList<>(list); next.add(m); return of(next);
    }
    /** Without magnet {@code i}. */
    Magnets without(int i) { ArrayList<Magnet> next = new ArrayList<>(list); next.remove(i); return of(next); }
    /** Magnet {@code i} moved to {@code m}, on top (it was brought to the front when picked up). */
    Magnets moved(int i, Magnet m) { ArrayList<Magnet> next = new ArrayList<>(list); next.remove(i); next.add(m); return of(next); }
    /** The topmost magnet whose tile holds page point {@code x, y}; -1 for none. */
    int hit(float x, float y) {
        for (int i = list.size() - 1; i >= 0; i--) if (holds(list.get(i), x, y)) return i;
        return -1;
    }
    @Override public boolean equals(Object o) { return o instanceof Magnets && ((Magnets) o).list.equals(list); }
    @Override public int hashCode() { return list.hashCode(); }

    // The tile

    /** A tile's width for {@code word}: the word ({@link #advance}), the room each side, in whole pairs, at least {@link #HEIGHT}. */
    static int width(String word) {
        float ems = 0;
        for (int i = 0; i < word.length(); i++) ems += advance(word.charAt(i));
        int w = (int) Math.ceil(ems * EM) + 2 * PAD;
        w += w & 1;
        return Math.max(HEIGHT, w);
    }
    /**
     * A character's advance in Roboto Mono, em: every glyph it has is 0.6 (its hmtx: 1229 of 2048 units), and it has
     * Latin, Greek, Cyrillic, Vietnamese and the common signs; anything else comes from a fallback face, given 1 em.
     */
    static float advance(char c) {
        if (c == '​') return 0;
        return c < 0x530 || c >= 0x1e00 && c < 0x1f00 || c >= 0x2000 && c < 0x2270 ? .6f : 1;
    }
    /** The tile's face as it lies untilted at its quarter turn, on the page: {left, top, right, bottom} (a moving tile's outline). */
    static int[] face(Magnet m) {
        int w = width(m.word) / 2, h = HEIGHT / 2;
        if ((m.turn & 1) == 1) { int t = w; w = h; h = t; }
        return new int[]{m.x - w, m.y - h, m.x + w, m.y + h};
    }
    /** The angle a tile is drawn at, degrees clockwise: its quarter turns and its tilt. */
    static double degrees(int tilt, int turn) { return 90 * (turn & 3) + tilt / 10.0; }
    /**
     * What a tile covers, its shadow and antialiased edges in, relative to its centre: {left, top, right, bottom} (left
     * and top negative), for {@code word} at {@code tilt} and {@code turn}. A tile's picture is this big, its centre at
     * {@code -left, -top}.
     */
    static int[] extent(String word, int tilt, int turn) {
        double w = width(word) / 2.0, h = HEIGHT / 2.0, a = Math.toRadians(degrees(tilt, turn)), c = Math.cos(a), s = Math.sin(a);
        double minX = 0, minY = 0, maxX = 0, maxY = 0;
        double[][] corners = {{-w, -h}, {w, -h}, {w, h}, {-w, h}, {-w + SHADOW, -h + SHADOW}, {w + SHADOW, -h + SHADOW}, {w + SHADOW, h + SHADOW}, {-w + SHADOW, h + SHADOW}};
        for (double[] p : corners) {
            double x = p[0] * c - p[1] * s, y = p[0] * s + p[1] * c;
            minX = Math.min(minX, x); maxX = Math.max(maxX, x); minY = Math.min(minY, y); maxY = Math.max(maxY, y);
        }
        return new int[]{(int) Math.floor(minX) - EDGE, (int) Math.floor(minY) - EDGE, (int) Math.ceil(maxX) + EDGE, (int) Math.ceil(maxY) + EDGE};
    }
    /** What magnet {@code m} covers on the page, {left, top, right, bottom}. */
    static int[] bounds(Magnet m) {
        int[] e = extent(m.word, m.tilt, m.turn);
        return new int[]{m.x + e[0], m.y + e[1], m.x + e[2], m.y + e[3]};
    }
    /** Whether page point {@code x, y} is on {@code m}'s tile (its face, not its shadow). */
    static boolean holds(Magnet m, float x, float y) {
        double a = -Math.toRadians(degrees(m.tilt, m.turn)), c = Math.cos(a), s = Math.sin(a), dx = x - m.x, dy = y - m.y;
        double u = dx * c - dy * s, v = dx * s + dy * c;
        return Math.abs(u) <= width(m.word) / 2.0 && Math.abs(v) <= HEIGHT / 2.0;
    }
    /**
     * {@code m} moved as little as needed for all of it, shadow too, to lie on a {@code width × height} page; centred
     * on an axis the page is too small for.
     */
    static Magnet inside(Magnet m, int width, int height) {
        int[] e = extent(m.word, m.tilt, m.turn);
        return m.at(clamp(m.x, -e[0], width - e[2], width), clamp(m.y, -e[1], height - e[3], height));
    }
    private static int clamp(int v, int low, int high, int size) { return low > high ? size / 2 : Math.max(low, Math.min(high, v)); }

    /**
     * Where {@code a} and {@code b} look different: the bounds of every magnet one has that the other hasn't (a
     * magnet moved shows in both places). Changing only which is on top shows nowhere: a moved magnet is new.
     */
    static List<int[]> changed(Magnets a, Magnets b) {
        Map<Magnet, Integer> count = new HashMap<>();
        for (Magnet m : a.list) count.merge(m, 1, Integer::sum);
        List<int[]> areas = new ArrayList<>();
        List<Magnet> added = new ArrayList<>();
        for (Magnet m : b.list) {
            Integer n = count.get(m);
            if (n == null || n == 0) added.add(m); else count.put(m, n - 1);
        }
        for (Map.Entry<Magnet, Integer> e : count.entrySet()) for (int i = 0; i < e.getValue(); i++) areas.add(bounds(e.getKey()));
        for (Magnet m : added) areas.add(bounds(m));
        return areas;
    }

    // Drawn into a picture (the PNG copy)

    /** A tile's picture: {@link #extent} big, ARGB, not premultiplied (Android's {@code getPixels}). */
    static final class Tile {
        final int[] argb;
        final int width, height;
        Tile(int[] argb, int width, int height) { this.argb = argb; this.width = width; this.height = height; }
    }
    /** Draws tiles: the app's {@code MagnetTiles} on the tablet; the checks' own on the desktop. */
    interface Tiles { Tile tile(Magnet m); }
    /**
     * Draws {@code magnets} over {@code argb}, a {@code width × height} opaque picture of the page (its PNG copy, a
     * notebook's page): each tile over what is under it, bottom first, clipped to the picture. The one way every copy
     * of a page gets its magnets.
     */
    static void stamp(int[] argb, int width, int height, Magnets magnets, Tiles tiles) {
        for (Magnet m : magnets.list) {
            Tile t = tiles.tile(m);
            int[] b = bounds(m);
            for (int ty = 0; ty < t.height; ty++) {
                int y = b[1] + ty;
                if (y < 0 || y >= height) continue;
                for (int tx = 0; tx < t.width; tx++) {
                    int x = b[0] + tx;
                    if (x < 0 || x >= width) continue;
                    int src = t.argb[ty * t.width + tx], alpha = src >>> 24;
                    if (alpha == 0) continue;
                    int i = y * width + x;
                    argb[i] = alpha == 255 ? src | 0xff000000 : argb[i] >>> 24 == 255 ? over(src, argb[i], alpha) : overClear(src, argb[i], alpha);
                }
            }
        }
    }
    /** {@link #over} on a pixel not wholly opaque (a page's Layers copy's Magnets layer, over nothing: {@link PageExport}), its coverage added to. */
    private static int overClear(int src, int dst, int alpha) {
        int below = dst >>> 24, keep = (below * (255 - alpha) + 127) / 255, cover = alpha + keep, out = cover << 24;
        if (cover == 0) return 0;
        for (int shift = 0; shift <= 16; shift += 8) {
            int c = (((src >> shift) & 255) * alpha + ((dst >> shift) & 255) * keep + cover / 2) / cover;
            out |= c << shift;
        }
        return out;
    }
    private static int over(int src, int dst, int alpha) {
        int keep = 255 - alpha, out = 0xff000000;
        for (int shift = 0; shift <= 16; shift += 8) {
            int c = (((src >> shift) & 255) * alpha + ((dst >> shift) & 255) * keep + 127) / 255;
            out |= c << shift;
        }
        return out;
    }

    // In a file: TSM5's WORD section

    /** The section's layout; a reader skips a version it doesn't know (the drawing opens without its magnets). */
    static final int VERSION = 1;
    /** The most a section may take, bytes. */
    static final int MOST_BYTES = 64 * 1024;
    /** The magnets as a WORD section's bytes: the version, how many, then each one's word, centre, tilt and turn. */
    byte[] encode() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeByte(VERSION); out.writeInt(list.size());
            for (Magnet m : list) { out.writeUTF(m.word); out.writeInt(m.x); out.writeInt(m.y); out.writeByte(m.tilt); out.writeByte(m.turn); }
            out.flush();
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
        return bytes.toByteArray();
    }
    /**
     * A WORD section's magnets, for a {@code width × height} page; {@link #NONE} for a version this reader doesn't know.
     * Anything else that doesn't read as magnets on that page is refused, as damage.
     */
    static Magnets decode(byte[] section, int width, int height) throws IOException {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(section));
            if (in.readUnsignedByte() != VERSION) return NONE;
            int count = in.readInt();
            if (count < 0 || count > MOST) throw new IOException("Invalid magnet count");
            ArrayList<Magnet> magnets = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                String word = in.readUTF();
                int x = in.readInt(), y = in.readInt(), tilt = in.readByte(), turn = in.readUnsignedByte();
                if (!validWord(word) || x < 0 || y < 0 || x > width || y > height || Math.abs(tilt) > MOST_TILT || turn > 3)
                    throw new IOException("Invalid magnet");
                magnets.add(new Magnet(word, x, y, tilt, turn));
            }
            if (in.read() != -1) throw new IOException("Extra magnet data");
            return of(magnets);
        } catch (java.io.EOFException | java.io.UTFDataFormatException e) { throw new IOException("Short magnet section", e); }
    }
    /** A word a tile can hold: 1 to {@link #MOST_LETTERS} characters, none of them a space or a control. */
    static boolean validWord(String word) {
        if (word.isEmpty() || word.length() > MOST_LETTERS) return false;
        for (int i = 0; i < word.length(); i++) { char c = word.charAt(i); if (Character.isWhitespace(c) || Character.isISOControl(c)) return false; }
        return true;
    }
}
