package io.github.mpdairy.monopaint;

/**
 * The nib the page outlines while the pen hovers ({@link DrawingPad}): what the tool would lay where the pen lands, page
 * pixels. Hovering reads no pressure, so it is the nib at {@link #TYPICAL}, the pressure a hand usually draws at, held
 * upright at a steady pace. No ring for the firmest nib (the owner, 2026-10-24: "make hover indicators more subtle. right
 * now they can disrupt the image"; a fainter dotted ring where pressing broadened it by half again until then). A shaped tip (chisel, flat nib, filbert) is its ellipse at its Nib Angle; bristles
 * a circle as wide as the swarm lands, a stipple its band at that touch; other patterns, placed lines and a smudge's stump a circle as far as
 * they reach. Pure, checked on the desktop.
 */
final class HoverNib {
    /** The pressure travel a hand usually draws at ({@link WildSample#TYPICAL_PRESS}). */
    static final double TYPICAL = WildSample.TYPICAL_PRESS;
    /**
     * A light touch's travel, a brush's ring: the smallest mark it lays where the pen lands, which a press only broadens
     * (the owner, 2026-10-29: "the dots reflect the normal size, not the light size, which is what i think it should be";
     * {@link #TYPICAL} until then). The owner's light strokes read .10 to .13 (docs/TILT_INPUT.md).
     */
    static final double LIGHT = .15;
    /** A pace at which the hand's Speed size leaves the nib as it is, px/ms ({@code clamp(v / 2.5)} is .3). */
    private static final double STEADY = .75;
    /** The ring is never drawn smaller than this radius, screen px: a fine nib's still shows where it is, clear of the line. */
    static final float MIN_RADIUS = 5;

    /** The ring's dots at most ({@code DrawingPad}'s hover ring), and its length a dot, px: a 5 px ring ({@link #MIN_RADIUS}) one, a 20 px one five. */
    static final int TICKS = 5;
    static final float TICK_SPACING = 24;
    /** The typical nib's long and short sides and its angle (radians, on the page). */
    final double width, height, angle;

    HoverNib(double width, double height, double angle) { this.width = width; this.height = height; this.angle = angle; }
    /** A round nib {@code diameter} across: the eraser's while it erases. */
    static HoverNib round(double diameter) { return new HoverNib(diameter, diameter, 0); }
    /**
     * The eraser at its own size, {@code most} px at its broadest: what a light touch erases ({@link #LIGHT}, as every
     * brush's ring; the owner, 2026-10-30: "make sure the eraser dot preview size is the lightest touch size"; a typical
     * touch's until then) ({@link InkWashSizes#diameter}).
     */
    static HoverNib eraser(double most) { return round(InkWashSizes.diameter((float) most, ToolSettings.pressure((float) LIGHT))); }
    /** {@code r}'s nib at a light touch ({@link #LIGHT}). */
    static HoverNib of(ResolvedBrush r) {
        double[] light = nib(r, LIGHT);
        return new HoverNib(light[0], light[1], light[2]);
    }
    /**
     * The nib {@code r} lays at pressure travel {@code p}, upright at a steady pace: its long side, short side and angle.
     * The widest of its line ({@link LineStamper#nib}), its body ({@link BodyStamper#shape}) and its smudge's stump.
     */
    static double[] nib(ResolvedBrush r, double p) {
        double[] nib = {0, 0, 0};
        if (r.line && r.lineTip == ResolvedBrush.LineTip.NIB && !r.linePlaced) {
            double a = Math.max(.6, r.pen.width(r.inkSize, p, STEADY) / 2), b = LineStamper.shortHalf(a, r.pen.aspect);
            wider(nib, 2 * a, 2 * b, r.pen.angle * LineStamper.DEG);
        } else if (r.line) {
            double d = r.lineTip == ResolvedBrush.LineTip.BRISTLES ? BristleSwarm.landing(r.lineSwarm, r.inkSize / 2, p, STEADY)
                    : r.lineTip == ResolvedBrush.LineTip.STIPPLE ? StippleStamper.reach(r.pattern, r.inkSize, p) : ModularStroke.lineReach(r);
            wider(nib, d, d, 0);
        }
        if (r.body != null && r.bodyTip == ResolvedBrush.BodyTip.BODY) {
            BodyStamper.Shape s = new BodyStamper.Shape();
            BodyStamper.shape(s, r.body, BodyStamper.diameter(r.body, r.bodySize, p, 0), 1, STEADY, 0, 0, Double.NaN);
            wider(nib, 2 * s.A, 2 * s.B, s.th);
        } else if (r.body != null) {
            double d = r.bodyTip == ResolvedBrush.BodyTip.BRISTLES ? BristleSwarm.landing(r.bodySwarm, r.bodySize / 2, p, STEADY)
                    : r.bodyTip == ResolvedBrush.BodyTip.STIPPLE ? StippleStamper.reach(r.pattern, r.bodySize, p) : ModularStroke.bodyReach(r);
            wider(nib, d, d, 0);
        }
        if (r.carry != null && r.carry.moves) wider(nib, 2 * r.carry.r, 2 * r.carry.r, 0);
        return nib;
    }
    private static void wider(double[] nib, double width, double height, double angle) {
        if (width > nib[0]) { nib[0] = width; nib[1] = height; nib[2] = angle; }
    }

    /** A ring's radius on screen for {@code length} page px across, the page shown {@code scale} times its size. */
    static float radius(double length, float scale) { return Math.max(MIN_RADIUS, (float) (length / 2 * scale)); }
    /** Whether {@code other} is this nib to within {@code step} px: the ring on screen needn't move. */
    boolean about(HoverNib other, double step) {
        return other != null && angle == other.angle && Math.abs(width - other.width) <= step && Math.abs(height - other.height) <= step;
    }
    /** The ring's ticks on an ellipse at {@code cx, cy}, half-axes {@code a, b}, turned {@code turn} radians: x, y pairs, the first on the long axis. */
    static float[] ticks(float cx, float cy, float a, float b, float turn) {
        float cos = (float) Math.cos(turn), sin = (float) Math.sin(turn);
        // The ellipse's length (Ramanujan's), so a long thin nib's ring counts as long as it reads.
        double h = Math.pow(a - b, 2) / Math.pow(a + b, 2), around = Math.PI * (a + b) * (1 + 3 * h / (10 + Math.sqrt(4 - 3 * h)));
        int n = (int) Math.max(1, Math.min(TICKS, Math.floor(around / TICK_SPACING)));
        if (n == 1) return new float[]{cx, cy};
        float[] at = new float[2 * n];
        for (int i = 0; i < n; i++) {
            double t = 2 * Math.PI * i / n;
            float u = a * (float) Math.cos(t), v = b * (float) Math.sin(t);
            at[2 * i] = cx + u * cos - v * sin; at[2 * i + 1] = cy + u * sin + v * cos;
        }
        return at;
    }
}
