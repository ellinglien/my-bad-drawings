package io.github.mpdairy.monopaint;

import static io.github.mpdairy.monopaint.StrokeMath.clamp;

/**
 * Smudge's Clear Water (the wet smudge spec, docs/archive/superpowers/specs/2026-10-19-wet-smudge-design.md): a brush that lays
 * only water. Under the pen it lifts what it touches as Lift does ({@link Carry}: the loose paint only, so permanent
 * paint and what masking fluid shields stay put), and marks how wet it leaves each pixel. At lift ({@link #lift}) Lay
 * Down Paint of what it lifted runs out through that wet, spreading softly and gathering a little at the wet's edge
 * (a bloom), never past it nor under masking fluid; the rest stays in the brush, so the page is paler where it worked.
 * What runs out is wet paint as a wash's is: with a Slow drying a share of it is held back and handed to the session's
 * {@link DryingWater} with the stroke's Drying, so it keeps settling for its drying time and runs downhill on a tilted
 * page ({@link GravityFlow}), as any Slow wash does; else it is laid at once. Over a wash that was still wet when the
 * stroke began ({@link WatercolorSession#stillWet}) it lifts more readily and runs further. On the water (Float) it is
 * Float's clear water ({@link #afloat}). Pure, checked on the desktop ({@code WetSmudgeChecks}).
 *
 * <p>After MonoPaint's clear-water wet brush pen (upstream 8743286, "Hold ink between strokes" off: "it lifts what it
 * touches and lets the lifted paint run out through the wet area"), in Inkwash's own engine.
 */
final class ClearWater {
    /** Over a wash still wet, it lifts this many times as readily ({@link Carry}), runs this much further and lays this much more of it back. */
    static final double WET_LIFT = 1.6, WET_SPREAD = 1.4, WET_LAY = 1.25;
    /** How far what it lifted runs, in dab radii (at least {@link #LEAST_SPREAD} px), and how much more gathers at the wet's edge. */
    static final double SPREAD = .8, LEAST_SPREAD = 3, BLOOM = .8;
    /** What runs out is laid on a grid of cells this many times coarser than its spread (1 to {@link #MOST_CELL} px), then shown smooth. */
    static final int MOST_CELL = 6;

    private ClearWater() { }

    /** What ran out at lift: its box on the page ({left, top, right, bottom}, inclusive) and, held back to settle, its darkness there row by row (null: laid in full). */
    static final class RunOut {
        final int[] at;
        final float[] wet;
        RunOut(int[] at, float[] wet) { this.at = at; this.wet = wet; }
    }

    /**
     * The lift of a Clear Water stroke that touched {@code rect} (page px, inclusive) through {@code plane} (its own
     * layer's, with {@link CarryPlane#water}): what it lifted runs out ({@link ClearWater}), laid on the plane as a
     * glaze. {@code stays}: it stays wet after lift (a Slow drying): the paint under its wet is wet again with what ran
     * out, a wash at lift, its darkness kept ({@link RunOut#wet}) and {@code held} of it (0 to 1, Drying's held-back
     * share) left off for now, to settle back after lift ({@link CarryPlane#rewet}).
     * {@code stillWet}: the box of a wash still wet when the stroke began, or null. On the page's thread. Null when
     * nothing ran out.
     */
    static RunOut lift(ToneDocument document, CarryPlane plane, ResolvedBrush.Carry c, int[] rect, int[] stillWet, double held, boolean stays) {
        int W = document.width, H = document.height;
        if (StrokeMath.empty(rect)) return null;
        boolean wetter = stillWet != null && stillWet[0] <= rect[2] && stillWet[2] >= rect[0] && stillWet[1] <= rect[3] && stillWet[3] >= rect[1];
        double sigma = Math.max(LEAST_SPREAD, c.r * SPREAD) * (wetter ? WET_SPREAD : 1), lay = Math.min(1, c.lay * (wetter ? WET_LAY : 1));
        if (!(lay > 0)) return null;
        int q = (int) clamp(Math.round(sigma / 4), 1, MOST_CELL), margin = (int) Math.ceil(2 * sigma) + 2 * q;
        int x0 = Math.max(0, rect[0] - margin), y0 = Math.max(0, rect[1] - margin), x1 = Math.min(W - 1, rect[2] + margin), y1 = Math.min(H - 1, rect[3] + margin);
        int cw = (x1 - x0) / q + 1, ch = (y1 - y0) / q + 1;
        float[] lifted = new float[cw * ch], wet = new float[cw * ch];
        int T = CarryPlane.SIDE;
        // What it lifted (the darkness found less what is there, where less) and how wet it is, cell by cell, over what it touched.
        for (int ty = Math.max(0, rect[1]) / T; ty <= Math.min(H - 1, rect[3]) / T; ty++)
            for (int tx = Math.max(0, rect[0]) / T; tx <= Math.min(W - 1, rect[2]) / T; tx++) {
                float[] wt = plane.wetIfAny(tx, ty);
                if (wt == null) continue;
                float[] P = plane.darknessTile(tx, ty), found = plane.foundTile(tx, ty);
                int left = tx * T, top = ty * T, w = Math.min(T, W - left), h = Math.min(T, H - top);
                for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                    int i = y * T + x;
                    if (wt[i] <= 0) continue;
                    int k = ((top + y - y0) / q) * cw + (left + x - x0) / q;
                    wet[k] += wt[i];
                    float d = found[i] - P[i];
                    if (d > 0) lifted[k] += d;
                }
            }
        double total = 0, area = q * (double) q;
        for (int k = 0; k < lifted.length; k++) { lifted[k] *= lay; total += lifted[k]; wet[k] /= area; }
        if (!(total > 1e-3) && !stays) return null;
        // It spreads softly (two box blurs), through the wet softened half as far, gathering at its edge, all it spreads kept.
        int reach = Math.max(1, (int) Math.round(.7 * sigma / q));
        float[] tmp = new float[cw * ch], spread = StrokeMath.blur(StrokeMath.blur(lifted, cw, ch, reach, tmp, new float[cw * ch]), cw, ch, reach, tmp, lifted);
        float[] water = StrokeMath.blur(wet, cw, ch, Math.max(1, reach / 2), tmp, wet);
        double kept = 0;
        for (int k = 0; k < spread.length; k++) {
            double m = clamp(1.5 * water[k]);
            water[k] = (float) m;
            spread[k] = (float) (spread[k] * m * (1 + BLOOM * 4 * m * (1 - m)));
            kept += spread[k];
        }
        double scale = kept > 0 ? Math.min(4, total / kept) / area : 0;
        // The box it ran out over and, staying wet, the wet's, by cell.
        int c0 = cw, c1 = -1, r0 = ch, r1 = -1;
        for (int cy = 0; cy < ch; cy++) for (int cx = 0; cx < cw; cx++) {
            int k = cy * cw + cx;
            if (!(spread[k] * scale > .002) && !(stays && water[k] > .01)) continue;
            c0 = Math.min(c0, cx); c1 = Math.max(c1, cx); r0 = Math.min(r0, cy); r1 = Math.max(r1, cy);
        }
        if (c1 < c0) return null;
        int[] at = {Math.max(0, x0 + (c0 - 1) * q), Math.max(0, y0 + (r0 - 1) * q), Math.min(W - 1, x0 + (c1 + 2) * q - 1), Math.min(H - 1, y0 + (r1 + 2) * q - 1)};
        int aw = at[2] - at[0] + 1;
        float[] out = stays ? new float[aw * (at[3] - at[1] + 1)] : null;
        plane.fill(at[0], at[1], at[2], at[3]);
        boolean masked = document.hasMaskLayer();
        double sc = scale;
        // Each pixel alone (its own place on the plane): in bands of rows on several cores.
        Cores.inBands(at[1], at[3], T, (long)aw * (at[3] - at[1] + 1), (top, bottom, worker) -> {
            for (int y = top; y <= bottom; y++) {
                double v = (y - y0 + .5) / q - .5;
                int cy = (int) Math.floor(v);
                double fy = v - cy;
                int ra = Math.max(0, Math.min(ch - 1, cy)) * cw, rb = Math.max(0, Math.min(ch - 1, cy + 1)) * cw;
                for (int x = at[0]; x <= at[2]; x++) {
                    double u = (x - x0 + .5) / q - .5;
                    int cx = (int) Math.floor(u);
                    double fx = u - cx;
                    int ca = Math.max(0, Math.min(cw - 1, cx)), cb = Math.max(0, Math.min(cw - 1, cx + 1));
                    double g = Math.min(.9, sc * ((spread[ra + ca] * (1 - fx) + spread[ra + cb] * fx) * (1 - fy) + (spread[rb + ca] * (1 - fx) + spread[rb + cb] * fx) * fy));
                    // Staying wet, the paint under the water is wet again: a wash at lift.
                    double m = stays ? (water[ra + ca] * (1 - fx) + water[ra + cb] * fx) * (1 - fy) + (water[rb + ca] * (1 - fx) + water[rb + cb] * fx) * fy : 0;
                    // Masking fluid keeps the water off what it shields.
                    if (masked) { double open = 1 - document.maskShield(x, y) / 255.0; g *= open; m *= open; }
                    if (!(g > .001) && !(m > .001)) continue;
                    double d = plane.rewet(x, y, g > .001 ? g : 0, m, held);
                    if (out != null) out[(y - at[1]) * aw + x - at[0]] = (float) d;
                }
            }
        });
        return new RunOut(at, out);
    }

    /**
     * Clear Water on the water (Float, while a bath floats on {@code document}'s selected pair): {@code b} as Float's
     * clear water, so it nudges and combs what floats rather than printing it; on the ink layer if the bath is there
     * (as Float's Clear water preset, an ink brush). Its Pick Up Paint is how strongly it drags.
     */
    static ModularBrush afloat(ModularBrush b, boolean inkLayer) {
        ModularBrush a = b.copy();
        a.set(BrushPart.LIFE, ModularBrush.switchTo(b.part(BrushPart.LIFE), PartChoice.FLOAT));
        if (inkLayer != ModularSizes.inks(a)) a.set(BrushPart.MEDIUM, ModularBrush.switchTo(b.part(BrushPart.MEDIUM), inkLayer ? PartChoice.INK : PartChoice.WASH));
        return a.normalize();
    }
}
