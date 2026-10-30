package io.github.mpdairy.monopaint;

import java.util.HashMap;
import java.util.Map;

/**
 * The paint a dirty brush keeps between strokes (Smudge's Keeps Paint, the wet smudge spec,
 * docs/archive/superpowers/specs/2026-10-19-wet-smudge-design.md): per holder (the smudge tool, the finger set to Smudge, a slot),
 * the reservoir a Drag or Smear stroke ended with ({@link Carry}): its cells' coverage and darkness (premultiplied, as
 * {@link CarryPlane}'s), {@code 2n + 1} a side, NaN where the brush never touched. The next stroke of that holder starts
 * with it ({@link Carry#Carry}), scaled to its own reservoir if its size differs, until {@link #clean}. Kept while the
 * app runs, not saved. Pure, checked on the desktop ({@code WetSmudgeChecks}).
 *
 * <p>MonoPaint's wet brush pen holds its ink the same way ("Hold ink between strokes", upstream 8743286,
 * {@code BrushInk}), as one shade for the whole brush; here it is a dab's worth of cells, so a smear's streaks carry on.
 *
 * <p>Paint Load (the oil paint spec, {@link LoadedPaint}): per holder too, how much paint its brush holds, a share of a
 * full load, from a rub on a grey or left by its last stroke; its next stroke starts with that, at least its Starting
 * Load. Kept while the app runs, not saved.
 *
 * <p>Stamp ({@link StampStamper}): per holder too, the ink its stamp has left and when it last printed, so prints keep
 * running dry from stroke to stroke until the pen rests. Kept while the app runs, not saved.
 *
 * <p>Wet brush (the wet brush spec, MonoPaint's wet brush pen's {@code BrushInk}): per holder too, how inky its brush is,
 * 0 (clear water) to 1 (black), as its last Wet ink stroke left it; a tap on a grey dips it ({@link #dip}): its next
 * stroke starts holding that grey. Kept between launches for the drawing screen's tools (the owner, 2026-10-22; {@link
 * #saveWetInkIn}), as MonoPaint keeps it.
 *
 * <p>Grey Shift ({@link SwatchPicker}): per holder too, where it is in its turn of greys (its deck, its pattern's
 * step), so each touch picks the next. Kept while the app runs, not saved.
 */
final class HeldPaint {
    /** A holder that isn't named: Brush Lab's test page, samples. */
    static final String ANY = "";
    /** A brush that holds more darkness than this somewhere shows as dirty. */
    static final float DIRTY = .04f;

    /** One holder's paint: its reservoir's half-side {@code n} and cells, row by row. */
    static final class Reservoir {
        final int n;
        final float[] a, p;
        Reservoir(int n, float[] a, float[] p) { this.n = n; this.a = a; this.p = p; }
        /**
         * The reservoir as one of half-side {@code m} holds it: each cell takes the nearest of these at the same place
         * across the dab (a smaller or bigger size swatch: the paint stays where it was on the brush). Itself for the same size.
         */
        Reservoir sized(int m) {
            if (m == n) return this;
            int side = 2 * m + 1, from = 2 * n + 1;
            float[] na = new float[side * side], np = new float[side * side];
            for (int j = 0; j < side; j++) for (int i = 0; i < side; i++) {
                int sj = clampCell((int) Math.round((j - m) * (double) n / Math.max(1, m)) + n, from), si = clampCell((int) Math.round((i - m) * (double) n / Math.max(1, m)) + n, from);
                na[j * side + i] = a[sj * from + si]; np[j * side + i] = p[sj * from + si];
            }
            return new Reservoir(m, na, np);
        }
        private static int clampCell(int k, int side) { return Math.max(0, Math.min(side - 1, k)); }
        /** Whether it holds paint that shows: darker than {@link #DIRTY} in some cell. */
        boolean dirty() {
            for (float v : p) if (v > DIRTY) return true;
            return false;
        }
        /** The paint's own darkness, 0 to 1: its cells' darkness over their coverage (both premultiplied), 0 where it holds none. */
        float darkness() {
            double cover = 0, dark = 0;
            for (int i = 0; i < a.length; i++) if (a[i] > 0 && !Float.isNaN(p[i])) { cover += a[i]; dark += p[i]; }
            return cover > 0 ? (float) Math.max(0, Math.min(1, dark / cover)) : 0;
        }
    }

    private final Map<String, Reservoir> held = new HashMap<>();
    private final Map<String, Double> loads = new HashMap<>();
    private final Map<String, StampStamper.Ink> inks = new HashMap<>();
    private final Map<String, SwatchPicker.State> shifts = new HashMap<>();
    private final Map<String, Float> wetInk = new HashMap<>();

    /** What {@code holder}'s brush holds, or null for a clean one. */
    synchronized Reservoir of(String holder) { return held.get(holder); }
    /** {@code holder}'s brush now holds {@code paint} (null: clean). */
    synchronized void keep(String holder, Reservoir paint) { if (paint == null) held.remove(holder); else held.put(holder, paint); }
    /** Whether {@code holder}'s brush holds paint that shows. */
    synchronized boolean dirty(String holder) { Reservoir r = held.get(holder); return r != null && r.dirty(); }
    /** Cleans {@code holder}'s brush (the smudge chooser's Clean Brush): its next stroke starts clean. Whether it held any. */
    synchronized boolean clean(String holder) { return held.remove(holder) != null; }
    /** Paint Load: the share of a full load {@code holder}'s brush holds ({@link LoadedPaint}), 0 for none beyond its Starting Load. */
    synchronized double load(String holder) { Double v = loads.get(holder); return v == null ? 0 : v; }
    /** {@code holder}'s brush now holds {@code share} of a full load (0: no more than its Starting Load). */
    synchronized void keepLoad(String holder, double share) { if (share > 0) loads.put(holder, share); else loads.remove(holder); }
    /** Wet brush: how inky {@code holder}'s brush is, 0 to 1, or NaN when it is freshly dipped (its next stroke starts in its grey). */
    synchronized float wetInk(String holder) {
        Float v = wetInk.get(holder);
        if (v == null && saved != null && !ANY.equals(holder)) {
            // Kept from the last launch (the owner, 2026-10-22: the brush's ink survives a restart, as MonoPaint's).
            v = readWetInk(saved.get(WET_INK + holder));
            if (v != null) wetInk.put(holder, v);
        }
        return v == null ? Float.NaN : v;
    }
    /** {@code holder}'s Wet brush now holds {@code fullness} of black. */
    synchronized void keepWetInk(String holder, float fullness) {
        wetInk.put(holder, fullness);
        if (saved != null && !ANY.equals(holder)) saved.put(WET_INK + holder, writeWetInk(fullness, 0));
    }
    /** A tap on a grey: {@code holder}'s Wet brush is dipped, its next stroke starting in the grey it draws with (MonoPaint: tap a colour to dip). */
    synchronized void dip(String holder) {
        wetInk.remove(holder);
        if (saved != null && !ANY.equals(holder)) saved.put(WET_INK + holder, null);
    }
    /**
     * Wet brush: where each named holder's ink is kept between launches ({@code wet_ink_} and the holder: its fullness
     * and tint, as MonoPaint keeps its {@code BrushInk}); null keeps it only while the app runs (Brush Lab's test page,
     * the checks). A dipped brush keeps nothing (it starts in its grey).
     */
    private BrushLibrary.Store saved;
    synchronized void saveWetInkIn(BrushLibrary.Store store) { saved = store; }
    static final String WET_INK = "wet_ink_";
    /** "fullness tint", each 0 to 1. */
    static String writeWetInk(float fullness, float tint) { return String.format(java.util.Locale.ROOT, "%.4f %.4f", fullness, tint); }
    /** The fullness kept as {@link #writeWetInk} wrote it, or null for none or what doesn't read. */
    static Float readWetInk(String value) {
        if (value == null) return null;
        try {
            float f = Float.parseFloat(value.trim().split("\\s+")[0]);
            return Float.isFinite(f) ? Math.max(0, Math.min(1, f)) : null;
        } catch (RuntimeException e) { return null; }
    }
    /** Stamp: the ink {@code holder}'s stamp has left and when it last printed ({@link StampStamper}); fresh for one that hasn't. */
    synchronized StampStamper.Ink stampInk(String holder) { StampStamper.Ink ink = inks.get(holder); return ink != null ? ink : StampStamper.Ink.FRESH; }
    /** {@code holder}'s stamp now has {@code ink} left. */
    synchronized void keepStampInk(String holder, StampStamper.Ink ink) { inks.put(holder, ink); }
    /** Grey Shift: where {@code holder} is in its turn of greys ({@link SwatchPicker#pick} moves it on). */
    synchronized SwatchPicker.State shifting(String holder) {
        SwatchPicker.State state = shifts.get(holder);
        if (state == null) shifts.put(holder, state = new SwatchPicker.State(new java.util.Random()));
        return state;
    }
}
