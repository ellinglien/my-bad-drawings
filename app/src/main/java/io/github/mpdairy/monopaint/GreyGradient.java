package io.github.mpdairy.monopaint;

/**
 * Gradient (the owner's "fading color (or gradient shift)"): a brush whose Control has it on changes its grey along each
 * stroke, by the distance drawn (cm, so the Manta and the Nomad shift alike), where Grey Shift ({@link SwatchPicker})
 * picks one between strokes. Fade lightens the stroke's grey to paper over its Length; To Grey takes it to its End
 * Grey (one of the panel's 16) over its Length; Cycle steps through Grey Shift's set (its Greys, Toolbar or Any, in
 * their order from the stroke's grey on, round and round), blending from one to the next over each Length, a rainbow
 * pencil's way. Past its Length a Fade or a To Grey stays where it ended. Each stroke starts again at its own grey (the
 * slot's, or Grey Shift's pick): one per stroke, kept by nothing.
 *
 * <p>How it reaches the dabs: the stroke is laid at the darkest grey it reaches ({@link #ref}), and each sample's share
 * of that ({@link #share}) goes into the samples' {@link StrokePoints#own}, which every stamper multiplies its dabs'
 * darkness by (Paint Load's, {@link LoadedPaint}, whose share it multiplies when both are on). The line's ink and a
 * glaze darken by their darkness times the grey, so the share is the ratio of the greys; a wash's body by its
 * strength ({@link ModularSizes#washStrength}), so the ratio of the strengths. The page keeps 0–255 tones; the panel
 * shows them in its 16 greys through its dots, as every in-between tone. Ink, Wash, Charcoal, Graphite and Crayon take
 * it; Opaque (its paint covers at its own grey, by coverage), Resist, Screentone and Float (a film on the water) don't,
 * and the lab hides its rows for them. A Fill lays its grey at once and doesn't shift ({@link FillTool#brush}). Pure,
 * checked on the desktop ({@code GradientChecks}).
 */
final class GreyGradient {
    /** Gradient's options: Off, Fade, To Grey, Cycle. */
    static final int OFF = 0, FADE = 1, TO_GREY = 2, CYCLE = 3;
    /** The End Grey's steps: the panel's 16, paper white (0) to black (15), a darkness of {@code k / 15} each. */
    static final int LEVELS = SwatchPicker.LEVELS;
    /** Page px a cm: the page's 300 dpi, as a Spiro's Loops per cm. */
    static final double PX_CM = SpiroStamper.PX_CM;

    /** The darkness at each stop: the stroke's own, then where it goes; a Length apart. */
    private final double[] stops;
    private final boolean cycles, washes;
    /** How far each shift takes, page px. */
    final double length;
    /** The darkest grey the stroke reaches: what it is laid at, each sample a share of it. */
    final double ref;
    /** The strength {@link #ref} lays at ({@link #strength}). */
    private final double refK;
    /** The last sample given its share ({@link #apply}). */
    private int at = -1;

    private GreyGradient(double[] stops, boolean cycles, double length, boolean washes) {
        this.stops = stops; this.cycles = cycles; this.length = length; this.washes = washes;
        double most = 0;
        for (double s : stops) most = Math.max(most, s);
        ref = most;
        refK = strength(most);
    }

    /** {@code b}'s Gradient: {@link #OFF}, {@link #FADE}, {@link #TO_GREY} or {@link #CYCLE}. */
    static int rule(ModularBrush b) { return (int) b.part(BrushPart.HAND).get(PartDial.GRADIENT, OFF); }
    /**
     * Whether {@code b}'s medium takes a grey per dab: Ink, Wash, Charcoal, Graphite and Crayon, not on water. Opaque
     * covers at its own grey, Resist and Screentone lay no grey of their own, Float lays a film at the lift.
     */
    static boolean takes(ModularBrush b) {
        PartChoice m = b.choice(BrushPart.MEDIUM);
        return (m == PartChoice.INK || m == PartChoice.WASH || m == PartChoice.CHARCOAL || m == PartChoice.GRAPHITE || m == PartChoice.CRAYON)
                && b.choice(BrushPart.LIFE) != PartChoice.FLOAT;
    }
    /** Whether {@code b}'s grey changes along its strokes: Gradient on, in a medium that takes it. */
    static boolean on(ModularBrush b) { return rule(b) != OFF && takes(b); }
    /** {@code b}'s End Grey, a panel step 0 (paper white) to 15 (black). */
    static int end(ModularBrush b) { return (int) Math.round(b.part(BrushPart.HAND).get(PartDial.GRAD_TO, PartDial.GRAD_TO.def)); }
    /** {@code b}'s Length, whole cm. */
    static long cm(ModularBrush b) { return Math.round(b.part(BrushPart.HAND).get(PartDial.GRAD_LEN, PartDial.GRAD_LEN.def)); }

    /**
     * A stroke of {@code b} starting at {@code start} darkness (0–1), {@code swatches} the toolbar's (Cycle's Toolbar
     * greys; null for their first); null when its grey doesn't change.
     */
    static GreyGradient of(ModularBrush b, double start, ToolSwatches swatches) {
        if (!on(b)) return null;
        start = StrokeMath.clamp(start);
        int rule = rule(b);
        double[] stops;
        if (rule == FADE) stops = new double[]{start, 0};
        else if (rule == TO_GREY) stops = new double[]{start, end(b) / (double) (LEVELS - 1)};
        else {
            // Cycle: from the stroke's grey to the set's next, round and round; from the nearest when it isn't in the set.
            int[] set = SwatchPicker.set(b);
            int n = set.length, near = 0;
            double[] greys = new double[n];
            for (int i = 0; i < n; i++) {
                greys[i] = SwatchPicker.darkness(b, swatches, set[i]);
                if (Math.abs(greys[i] - start) < Math.abs(greys[near] - start)) near = i;
            }
            int first = Math.abs(greys[near] - start) < 1e-6 ? near + 1 : near;
            stops = new double[n + 1];
            stops[0] = start;
            for (int i = 0; i < n; i++) stops[1 + i] = greys[(first + i) % n];
        }
        return new GreyGradient(stops, rule == CYCLE, cm(b) * PX_CM, ModularSizes.washes(b));
    }

    /** The darkness {@code d} page px along the stroke. */
    double darkness(double d) {
        double u = Math.max(0, d) / length;
        if (!cycles) return StrokeMath.lerp(stops[0], stops[1], Math.min(1, u));
        // Cycle: a Length from each stop to the next, eased in and out so each grey lingers.
        int m = (int) Math.floor(u);
        return StrokeMath.lerp(stop(m), stop(m + 1), StrokeMath.smooth(u - m));
    }
    /** Cycle's {@code m}th stop: the stroke's grey, then the set's round and round. */
    private double stop(int m) { return m == 0 ? stops[0] : stops[1 + (m - 1) % (stops.length - 1)]; }
    /** What a grey of {@code darkness} lays at: a wash's body its strength, any other mark the darkness itself. */
    private double strength(double darkness) { return washes ? ModularSizes.washStrength(darkness) : darkness; }
    /** The share of {@link #ref} the marks lay at {@code darkness}: 1 at the darkest, 0 at paper. */
    double share(double darkness) { return refK > 0 ? strength(darkness) / refK : 0; }
    /** The share at {@code d} page px along the stroke. */
    double shareAt(double d) { return share(darkness(d)); }

    /**
     * Writes the newest of {@code pts}' samples' shares into their {@link StrokePoints#own}: over Paint Load's
     * ({@code loaded}: it wrote theirs first), else as they are.
     */
    void apply(StrokePoints pts, boolean loaded) {
        pts.owns();
        float[] own = pts.own;
        for (int k = at + 1; k < pts.count; k++) {
            float s = (float) shareAt(pts.d[k]);
            own[k] = loaded ? own[k] * s : s;
        }
        at = pts.count - 1;
    }

    /** End Grey's step {@code k} as it shows: White, Black, else its darkness ("47%"). */
    static String greyShown(int k) {
        return k <= 0 ? "White" : k >= LEVELS - 1 ? "Black" : Math.round(100.0 * k / (LEVELS - 1)) + "%";
    }
    /** "This brush"'s sentence for Gradient, by its rule; null when it is off (or its medium doesn't take it). */
    static String words(ModularBrush b) {
        if (!on(b)) return null;
        String over = cm(b) + " cm.";
        switch (rule(b)) {
            case FADE: return "Fades to paper over " + over;
            case TO_GREY: {
                int k = end(b);
                return k <= 0 ? "Fades to paper over " + over : "Shifts to " + (k >= LEVELS - 1 ? "black" : greyShown(k) + " grey") + " over " + over;
            }
            default: return "Cycles through its greys every " + over;
        }
    }
}
