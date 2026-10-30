package io.github.mpdairy.monopaint;

/**
 * Smudge's Paint Load (the oil paint spec, docs/archive/superpowers/specs/2026-10-19-oil-paint-design.md): MonoPaint's "Oil
 * paint mode when opaque" (upstream fe6786d, {@code PaintLoad}, {@code ToolSettings.loadedBy}) in the six-part engine. A
 * brush holds at most a full load of paint: {@link #full} page px of stroke at its full width. Each stroke starts with a
 * share of it, the Starting Load, or what a rub on a grey loaded ({@link HeldPaint#load}); its own paint is laid
 * solidly at first and runs out along the stroke ({@link Run}), a wider dab spending more, its share left falling
 * smoothly to nothing ({@link #amount}), so the brush lays less of its own paint and more of what its smudge picked
 * up, and once empty it only smudges: over clean paper it leaves nothing. What a stroke leaves on the brush starts the
 * holder's next one, at least the Starting Load. Pure, checked on the desktop ({@code OilChecks}).
 */
final class LoadedPaint {
    /** How far a full load lays at Paint Load 1, page px of full-width stroke, for a brush {@link #REF_PX} wide. */
    static final double FULL_PX = 3000;
    /** The width a full load's length is given for; a broader brush holds more, a narrower less, by the root of its width (half to twice). */
    static final double REF_PX = 60;
    /**
     * A rub's load ({@link #rubbed}): page px of stroke a screen px of the pen's travel over a grey loads (MonoPaint's
     * Loading speed: about as far as the pen rubbed; twice that here, so a few passes over the bar's small faces fill it).
     */
    static final double RUB = 2;
    /** How far, dp, the pen moves over a grey before it is a rub that loads rather than a tap that picks. */
    static final float RUB_DP = 6;
    /** A step shorter than this, dp, is the pen's or finger's tremor, not travel: a still long press must not add up to a rub. */
    static final float TREMOR_DP = 2;
    /** Whether a step of {@code step} px, {@code dp} px a dp, counts toward a rub's travel. */
    static boolean travels(float step, float dp) { return step > TREMOR_DP * dp; }

    /** How far a full load lays, page px of full-width stroke. */
    final double full;
    /** The share of a full load every stroke starts with (Starting Load). */
    final double start;

    private LoadedPaint(double full, double start) { this.full = full; this.start = start; }

    /**
     * Smudge part {@code carry}'s Paint Load for a brush {@code size} page px wide (its body's, else its line's), or null
     * when it has none: off, or a smudge without it.
     */
    static LoadedPaint of(ModularBrush.Part carry, double size) {
        double load = carry.get(PartDial.PLOAD, 0);
        if (!(load > 0)) return null;
        double scale = StrokeMath.clamp(Math.sqrt(Math.max(1, size) / REF_PX), .5, 2);
        return new LoadedPaint(FULL_PX * load * load * scale, StrokeMath.clamp(carry.get(PartDial.PMIN, PartDial.PMIN.def)));
    }

    /** The share a stroke starts with when its brush holds {@code held} (0 for nothing held): that, at least the Starting Load, at most full. */
    double startShare(double held) { return StrokeMath.clamp(Math.max(start, held)); }
    /**
     * How much of its own paint a brush that started with {@code share} still lays after spending {@code spent} px:
     * 1, holding at first, falling smoothly to 0 where the share is spent (MonoPaint's PaintLoad.travel); 0 from the
     * start for an empty brush.
     */
    double amount(double share, double spent) {
        double length = share * full;
        if (!(length > 0)) return 0;
        double t = Math.min(1, spent / length);
        return 1 - t * t * (3 - 2 * t);
    }
    /** The share left after spending {@code spent} px from {@code share}, 0 at least. */
    double left(double share, double spent) { return Math.max(0, share - spent / full); }
    /**
     * The share a rub of {@code travel} screen px over a grey loads into a brush holding {@code held}: from what it
     * holds, at least the Starting Load, as far again as {@link #RUB} times the rub, at most full (MonoPaint's
     * ToolSettings.loadedBy). Screen px are page px at 100%: the load is the stroke the rub would lay there.
     */
    double rubbed(double held, double travel) {
        return StrokeMath.clamp(startShare(held) + Math.max(0, travel) * RUB / full);
    }
    /** Whether the pen moving {@code travel} px over a grey, {@code dp} px a dp, is a rub rather than a tap. */
    static boolean rub(float travel, float dp) { return travel > RUB_DP * dp; }

    /** {@code brush}'s Paint Load as it draws at {@code look} (null for the eraser), or null when it has none. */
    static LoadedPaint of(Brush brush, ToolSwatches.Look look) {
        if (brush == null || !(brush.parts.part(BrushPart.CARRY).get(PartDial.PLOAD, 0) > 0)) return null;
        return ResolvedBrush.of(brush.parts, look.px, look.darkness).loaded;
    }
    /**
     * A rub of {@code travel} screen px on a grey ({@link SwatchFace}) loads {@code holder}'s brush, {@code brush} drawing at
     * {@code look}, into {@code held} ({@link #rubbed}); whether it did: only a brush with Paint Load loads.
     */
    static boolean rub(HeldPaint held, String holder, Brush brush, ToolSwatches.Look look, double travel) {
        LoadedPaint p = of(brush, look);
        if (p == null) return false;
        held.keepLoad(holder, p.rubbed(held.load(holder), travel));
        return true;
    }
    /** The share of a full load {@code holder}'s next stroke with {@code brush} at {@code look} starts with ({@link #startShare}), or NaN without Paint Load: what the bar shows. */
    static double shown(HeldPaint held, String holder, Brush brush, ToolSwatches.Look look) {
        LoadedPaint p = of(brush, look);
        return p == null ? Double.NaN : p.startShare(held.load(holder));
    }

    /**
     * How much wider or narrower than its full width a stroke of {@code r} lays at pressure {@code pr} and speed {@code v}
     * (px/ms): its body's dab ({@link BodyStamper#diameter}), else its line's, 1 at a full press. A wider dab spends paint
     * faster.
     */
    static double widthShare(ResolvedBrush r, double pr, double v) {
        if (r.body != null) return BodyStamper.diameter(r.body, 1, pr, 0);
        ResolvedBrush.Pen pen = r.pen;
        return pen.width(1, pr, v) / pen.width(1, 1, 0);
    }

    /**
     * One stroke's paint: it starts with {@link #share}, spends a dab's width at each sample, and writes how much of its
     * own paint it still lays at each sample into the samples' {@link StrokePoints#own}, which every stamper reads.
     */
    static final class Run {
        final LoadedPaint paint;
        final double share;
        private final ResolvedBrush r;
        private double spent;
        /** The last sample spent for. */
        private int at = -1;

        Run(ResolvedBrush r, double share) { this.r = r; this.paint = r.loaded; this.share = share; }
        /** Spends up to the newest of {@code pts}' samples and writes their {@link StrokePoints#own}. */
        void spend(StrokePoints pts) {
            pts.owns();
            for (int k = at + 1; k < pts.count; k++) {
                if (k > 0) spent += ((double) pts.d[k] - pts.d[k - 1]) * widthShare(r, pts.p[k], pts.v[k]);
                pts.own[k] = (float) paint.amount(share, spent);
            }
            at = pts.count - 1;
        }
        /** The share left on the brush. */
        double left() { return paint.left(share, spent); }
        /** Page px spent so far, of full-width stroke. */
        double spent() { return spent; }
    }
}
