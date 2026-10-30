package io.github.mpdairy.monopaint;

public final class HoverNibChecks {
    public static void main(String[] args) throws java.io.IOException {
        Assets.install(Assets.folder(Assets.FOLDER));
        eraser(); brushes(); everyPreset(); onArrival(); zoom(); atRest(); dots();
        System.out.println("PASS: the hover ring is a brush's nib at a light touch (2026-10-29), the eraser's at a typical one at its own size, no firmest ring (2026-10-24, more subtle), a shaped tip's ellipse at its angle, scaled with zoom and never under 5 px; the readout's nib at rest is a chisel's or a hake's shape, a round pen's circle; the ring's dots one a 24 px of it, a lone dot where the finest lands, five at most (2026-10-29)");
    }
    /** The side column's readout ({@link FootprintSweep#atRest}): the shape of the nib at rest, its widest mark's long side over its short. */
    static double restAspect(String preset, double px) {
        double[] widest = {0, 1};
        for (double[] m : FootprintSweep.atRest(ResolvedBrush.of(preset(preset).parts, px, .8))) if (m[0] > widest[0]) widest = m;
        return widest[0] / widest[1];
    }
    private static Brush preset(String name) {
        for (ToolPresets.Preset p : ToolPresets.all()) if (p.name.equals(name)) return p.brush();
        throw new AssertionError("no preset " + name);
    }
    private static void atRest() {
        for (String round : new String[]{"Ballpoint", "Round marker", "Soft wash"}) near(restAspect(round, 24), 1, round + " shows round in the readout");
        for (String shaped : new String[]{"Chisel marker", "Wet hake"}) check(restAspect(shaped, 24) > 1.5, shaped + " shows its shape in the readout, not a circle (" + restAspect(shaped, 24) + ")");
    }
    private static void eraser() {
        for (InkWashSizes.Preset preset : InkWashSizes.Preset.values()) {
            double max = InkWashSizes.maxSize(preset), typical = InkWashSizes.TIP + (max - InkWashSizes.TIP) * .36;
            HoverNib nib = HoverNib.eraser(max);
            near(nib.width, InkWashSizes.diameter((float) max, ToolSettings.pressure((float) HoverNib.LIGHT)), "The eraser's ring is what a light touch erases, as every brush's");
            check(nib.width < typical && nib.width >= InkWashSizes.TIP, "less than a typical touch's, at least the tip (" + nib.width + " of " + typical + ")");
            check(nib.width == nib.height, "and round");
        }
        check(HoverNib.eraser(120).width < 44.48, "M's ring is under the 44 px a typical touch erases: " + HoverNib.eraser(120).width);
    }
    private static void brushes() {
        for (String name : new String[]{"brave-onion", "wet-wash", "thick-oil", "soft-coal", "wide-hake", "rake"}) {
            ResolvedBrush r = ResolvedBrush.of(ModularCodeChecks.fixture(name), 16, 1);
            HoverNib nib = HoverNib.of(r);
            check(nib.width > 0 && nib.height > 0 && nib.height <= nib.width + 1e-9, name + ": a nib, its long side first");
            check(HoverNib.nib(r, 1)[0] >= nib.width, name + ": pressing firmly never lays a narrower nib");
        }
        // A wash whose pressure sizes it: its ring the dab a light touch lays (the owner, 2026-10-29: "the dots reflect the
        // normal size, not the light size, which is what i think it should be"), a firm one all 16 px.
        ResolvedBrush wash = ResolvedBrush.of(ModularCodeChecks.fixture("wet-wash"), 16, 1);
        double tip = .6 + (.06 - .6) * wash.body.pressure, d = 16 * (tip + (1 - tip) * Math.pow(HoverNib.LIGHT, 1.2));
        HoverNib washNib = HoverNib.of(wash);
        near(washNib.width, d, "The wash's ring is its dab at a light touch");
        check(washNib.width < HoverNib.nib(wash, HoverNib.TYPICAL)[0], "a light touch's ring inside a typical touch's");
        // A flat nib's paint is its ellipse, at its Nib Angle.
        ResolvedBrush oil = ResolvedBrush.of(ModularCodeChecks.fixture("thick-oil"), 16, 1);
        HoverNib oilNib = HoverNib.of(oil);
        near(oilNib.height / oilNib.width, oil.body.aspect, "A flat nib's ring is its ellipse"); near(oilNib.angle, oil.body.angle * LineStamper.DEG, "at its Nib Angle");
        // Charcoal's line: as wide as LineStamper lays it at a typical touch.
        ResolvedBrush coal = ResolvedBrush.of(ModularCodeChecks.fixture("soft-coal"), 16, 1);
        near(HoverNib.of(coal).width, 16 * (1 - .75 * coal.pen.pressure + 1.35 * coal.pen.pressure * HoverNib.LIGHT), "Charcoal's ring is its line at a light touch");
        check(HoverNib.of(ResolvedBrush.of(ModularCodeChecks.fixture("soft-coal"), 48, 1)).width > HoverNib.of(coal).width, "A larger size, a larger ring");
    }
    /** Every kind sizes its ring at a light touch (the owner, 2026-10-29: a watercolour that "starts small" showed its normal size; bristles and stipple rang as far as they reach until then). */
    private static void everyPreset() {
        int swarms = 0;
        for (ToolPresets.Preset p : ToolPresets.all()) {
            ResolvedBrush r = ResolvedBrush.of(p.brush().parts, 24, 1);
            double light = HoverNib.of(r).width, typical = HoverNib.nib(r, HoverNib.TYPICAL)[0], firm = HoverNib.nib(r, 1)[0];
            check(light >= 0 && light <= typical + 1e-9 && typical <= firm + 1e-9, p.name + ": light ring " + light + " within typical " + typical + " within firm " + firm);
            boolean swarm = r.line && r.lineTip == ResolvedBrush.LineTip.BRISTLES || r.body != null && r.bodyTip == ResolvedBrush.BodyTip.BRISTLES;
            if (swarm) { swarms++; check(light < ModularStroke.reach(r), p.name + ": bristles ring where they land, not as far as they splay (" + light + " vs " + ModularStroke.reach(r) + ")"); }
        }
        check(swarms > 0, "some preset has bristles");
    }
    /** The owner (2026-10-29): the dots were "like a little bug on the screen demanding my attention". */
    static void onArrival() throws java.io.IOException {
        String pad = DrawLayoutChecks.source("DrawingPad");
        check(pad.contains("static final long NIB_SHOWN_MS = 1000;") && pad.contains("if (event.getEventTime() >= nibShownUntil) return;"), "The ring shows a second as the pen arrives, then goes");
        check(pad.contains("if (stroke == null) hideOutline();\n        penArrives = true;"), "The pen arrives again once away PEN_AWAY_MS, not at a stroke's lift");
        check(pad.contains("if (hoverNib != null && !hoverNib.about(was, 0)) penArrives = true;"), "A new tool or size shows the ring again");
        check(pad.contains("nibShownUntil = SystemClock.uptimeMillis() + NIB_SHOWN_MS;") && pad.contains("if (SystemClock.uptimeMillis() >= nibShownUntil) { hideOutline(); return; }"),
                "A finger erasing shows its ring a second from the touch, then it goes");
    }
    private static void zoom() {
        check(HoverNib.radius(44, 1) == 22 && HoverNib.radius(44, 2.5f) == 55 && HoverNib.radius(44, .5f) == 11, "The ring scales with zoom");
        check(HoverNib.radius(3, 1) == HoverNib.MIN_RADIUS && HoverNib.radius(30, .1f) == HoverNib.MIN_RADIUS, "A fine nib's ring stays 5 px, clear of its line");
        HoverNib a = new HoverNib(40, 20, 0);
        check(a.about(new HoverNib(41, 21, 0), 2) && !a.about(new HoverNib(45, 20, 0), 2) && !a.about(new HoverNib(40, 20, .5), 2) && !a.about(null, 2),
                "The ring moves only once the nib changed more than a step");
    }
    private static void near(double actual, double expected, String message) { check(Math.abs(actual - expected) < 1e-3 * Math.max(1, Math.abs(expected)), message + " (" + actual + " vs " + expected + ")"); }
    /** The owner (2026-10-29): "at small sizes the 8 dots seems like too many.. you could get away with even 1 at super-fine sizes". */
    static void dots() {
        float[] fine = HoverNib.ticks(50, 60, HoverNib.MIN_RADIUS, HoverNib.MIN_RADIUS, 0);
        check(fine.length == 2 && fine[0] == 50 && fine[1] == 60, "The finest nib: one dot where it lands");
        check(HoverNib.ticks(0, 0, 10, 10, 0).length == 4 && HoverNib.ticks(0, 0, 15, 15, 0).length == 6, "A 10 px ring two dots, 15 px three");
        check(HoverNib.ticks(0, 0, 20, 20, 0).length == 10 && HoverNib.ticks(0, 0, 200, 200, 0).length == 2 * HoverNib.TICKS, "Five from 20 px, never more");
        check(HoverNib.ticks(0, 0, 30, 2, 0).length > 4, "A long thin nib counts its ring's length, not its width");
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError("FAIL: " + message); }
}
