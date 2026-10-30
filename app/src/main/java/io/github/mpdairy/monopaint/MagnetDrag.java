package io.github.mpdairy.monopaint;

import java.util.ArrayList;
import java.util.List;

/**
 * A magnet under the pen ({@link WordsMode}): picked up from the page, or a new one from the bin, it follows the pen,
 * keeping where it was held, until the pen lifts. Then it stays where it is (moved fully onto the page), or, lifted
 * over the bin or off the page, it is taken off; a magnet barely moved stays as it was. While it moves the tile stays
 * where it was and only its outline follows the pen ({@link #outline}), the cheapest thing e-ink can show moving; at
 * lift it is laid at its new place. Pure, checked on the desktop ({@code MagnetChecks}).
 */
final class MagnetDrag {
    /** A pen moving less than this, page px (about 1 mm), from where it landed hasn't moved the magnet. */
    static final float STILL = 12;
    /** The page's magnets as the pen found them. */
    final Magnets base;
    /** The magnet's place in {@link #base}, or -1 for a new one from the bin. */
    final int index;
    /** The magnet as it was picked up (a new one: where it was put down). */
    final Magnets.Magnet picked;
    private final float dx, dy, downX, downY;
    private boolean moved;
    /** Where it is now: its centre follows the pen. */
    private Magnets.Magnet at;

    /** Picks up magnet {@code index} of {@code base} (-1: {@code picked} is new) with the pen at page {@code penX, penY}. */
    MagnetDrag(Magnets base, int index, Magnets.Magnet picked, float penX, float penY) {
        this.base = base; this.index = index; this.picked = picked; at = picked;
        dx = picked.x - penX; dy = picked.y - penY; downX = penX; downY = penY;
    }
    /** The magnet where it is now. */
    Magnets.Magnet at() { return at; }
    /** A new magnet, not yet on the page. */
    boolean fresh() { return index < 0; }
    /** The pen at {@code penX, penY}: the magnet's new place, and whether it moved (once moved past {@link #STILL}, it follows exactly). */
    boolean moveTo(float penX, float penY) {
        if (!moved && Math.hypot(penX - downX, penY - downY) < STILL) return false;
        moved = true;
        Magnets.Magnet next = picked.at(Math.round(penX + dx), Math.round(penY + dy));
        if (next.equals(at)) return false;
        at = next; return true;
    }
    /**
     * The page's magnets once the pen lifts at {@code penX, penY} on a {@code width × height} page, {@code overBin} or
     * not: without it when lifted over the bin or off the page; else with it where it is, moved fully onto the page and
     * on top; {@link #base} itself when nothing changed (a picked-up magnet barely moved).
     */
    Magnets lift(float penX, float penY, int width, int height, boolean overBin) {
        moveTo(penX, penY);
        boolean off = overBin || penX < 0 || penY < 0 || penX >= width || penY >= height;
        if (off) return fresh() ? base : base.without(index);
        if (!fresh() && !moved) return base;
        Magnets.Magnet placed = Magnets.inside(at, width, height);
        return fresh() ? base.with(placed) : base.moved(index, placed);
    }

    // Who a landing belongs to

    /** What a pointer landing on the page does ({@link WordsMode#touch}): draws, picks up a magnet, lays a new one, or nothing. */
    enum Landing { DRAWING, MAGNET, NEW, NOTHING }
    /**
     * A pointer landing on the page, the pen or not, with the bin open or not, on a magnet or not, with a word picked
     * in the bin or not; {@code mayPick}: the canvas isn't busy with another gesture. The pen on a magnet always picks it
     * up, with the bin closed too (before 2026-10-19 only an open bin let it, and the pen drew under them); a word picked
     * goes where the pen lands, over any magnet there. With the bin open nothing else draws, the pen or a finger; with it
     * closed everything else does. Only the pen moves magnets: a finger would fight the finger tool, a palm carry them off.
     */
    static Landing landing(boolean binOpen, boolean pen, boolean mayPick, boolean onMagnet, boolean wordPicked) {
        if (!binOpen) return pen && mayPick && onMagnet ? Landing.MAGNET : Landing.DRAWING;
        if (!pen) return Landing.NOTHING;
        return wordPicked ? Landing.NEW : onMagnet ? Landing.MAGNET : Landing.NOTHING;
    }

    // The outline that follows the pen

    /** The outline's width, px: 2 of ink outside 1 of paper, so it shows on paper and on ink alike. */
    static final int OUTLINE = 3;
    /**
     * What shows the magnet while it moves: its face's outline, untilted ({@link Magnets#face}), as four bands that never
     * overlap (top, bottom, left, right), so a move draws and puts back only them, a few thousand pixels, not the tile.
     */
    static List<int[]> outline(Magnets.Magnet m) {
        int[] f = Magnets.face(m);
        int t = OUTLINE;
        List<int[]> bands = new ArrayList<>(4);
        bands.add(new int[]{f[0], f[1], f[2], f[1] + t});
        bands.add(new int[]{f[0], f[3] - t, f[2], f[3]});
        bands.add(new int[]{f[0], f[1] + t, f[0] + t, f[3] - t});
        bands.add(new int[]{f[2] - t, f[1] + t, f[2], f[3] - t});
        return bands;
    }

    // Rectangles: {left, top, right, bottom}

    /** The parts of {@code a} outside {@code b}: none, {@code a} itself, or up to four bands. */
    static List<int[]> minus(int[] a, int[] b) {
        List<int[]> out = new ArrayList<>();
        if (b == null || b[0] >= a[2] || b[2] <= a[0] || b[1] >= a[3] || b[3] <= a[1]) { out.add(a.clone()); return out; }
        int top = Math.max(a[1], b[1]), bottom = Math.min(a[3], b[3]);
        if (b[1] > a[1]) out.add(new int[]{a[0], a[1], a[2], b[1]});
        if (b[3] < a[3]) out.add(new int[]{a[0], b[3], a[2], a[3]});
        if (b[0] > a[0]) out.add(new int[]{a[0], top, b[0], bottom});
        if (b[2] < a[2]) out.add(new int[]{b[2], top, a[2], bottom});
        return out;
    }
    /** {@code a} and {@code b} together: the least rectangle holding both. */
    static int[] union(int[] a, int[] b) { return new int[]{Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[2], b[2]), Math.max(a[3], b[3])}; }
    /** Its area, px. */
    static long area(int[] r) { return Math.max(0L, r[2] - r[0]) * Math.max(0, r[3] - r[1]); }
}
