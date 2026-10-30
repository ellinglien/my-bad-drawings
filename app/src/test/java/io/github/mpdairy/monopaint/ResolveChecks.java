package io.github.mpdairy.monopaint;

public final class ResolveChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        onion(); wash(); oil(); coal(); hake(); rake(); pencil(); fallbacks();
        System.out.println("PASS: parts resolve to the prototype's line, body and settle for split nib, wash, cover with impasto, charcoal, hake, multi-line and graphite brushes, at 16 and 360 px; bristles draw as their swarm; Smudge resolves as the lab's");
    }
    static ResolvedBrush r(String name, double size) { return ResolvedBrush.of(ModularCodeChecks.fixture(name), size, 1); }
    static void onion() {
        ResolvedBrush r = r("brave-onion", 16);
        near(r.inkSize, 16.00617760617761, "inkSize"); near(r.washSize, 128.04942084942087, "washSize"); near(r.bodySize, 52.45081734590586, "bodySize");
        near(r.inkK, 1, "inkK"); near(r.washK, .34, "washK"); near(r.windowLen, 62.03552123552123, "windowLen"); near(r.placeSize, 52.45081734590586, "placeSize");
        check(r.lineTip == ResolvedBrush.LineTip.NIB && r.linePlaced && r.bodyTip == ResolvedBrush.BodyTip.BODY && r.line && r.body != null && r.lineReach == 96
                && r.life == ResolvedBrush.Life.WET && r.rule == ResolvedBrush.Rule.INK && r.paper == BrushTexture.LAID, "brave-onion's stampers");
        ResolvedBrush.Pen p = r.pen;
        near(p.aspect, .4198764478764479, "aspect"); near(p.angle, 35.01930501930502, "angle"); near(p.taperOut, .4, "taperOut"); near(p.pressure, .3998455598455599, "pressure");
        near(p.tooth, .13992277992277993, "tooth"); near(p.pool, .175038535501856, "pool"); near(p.spread, .6996138996138996, "spread"); near(p.breathe, .3998455598455599, "breathe");
        near(p.wetGap, .5406949806949807, "wetGap"); near(p.tiltAngle, .9000384609651019, "tiltAngle");
        check(p.lines == ResolvedBrush.Lines.SPLIT && p.pWidth == 0 && !p.fast && p.speed == 0 && !p.thicker, "a split nib breathes inside its placement");
        near(ResolvedBrush.of(ModularCodeChecks.fixture("brave-onion"), 16, .5).inkK, .5, "inkK follows the shade");
        near(ResolvedBrush.of(ModularCodeChecks.fixture("brave-onion"), 16, .53).gray, 255 * .47, "gray is the app's paper times the shade's lightness");
    }
    static void wash() {
        ResolvedBrush r = r("wet-wash", 16);
        check(!r.line && r.body != null && r.body.wet && r.life == ResolvedBrush.Life.DRY && r.cover == 0, "A wet wash is a body, drying");
        near(r.bodySize, 16.00617760617761, "bodySize"); near(r.inkSize, 1.600617760617761, "inkSize"); near(r.windowLen, 80.0030888030888, "windowLen"); near(r.settle, 777 / 1295.0, "settle");
        near(r.body.pressure, .7996911196911198, "body pressure"); near(r.body.soft, .4, "soft"); near(r.body.gran, .30038610038610036, "body gran");
        near(r.look.hollow, .5003861003861004, "hollow"); near(r.look.bloom, .3521853281853282, "bloom"); near(r.look.backrun, .1760926640926641, "backrun"); near(r.look.gran, .30038610038610036, "gran");
        ResolvedBrush big = r("wet-wash", 360);
        near(big.washSize, 360.1389961389962, "washSize at 360"); near(big.windowLen, 252.0694980694981, "windowLen at 360");
    }
    static void oil() {
        ResolvedBrush r = r("thick-oil", 16);
        check(r.cover == 1 && r.body != null && !r.body.wet && r.life == ResolvedBrush.Life.WET, "Cover is opaque flat paint");
        near(r.body.aspect, .3202471042471043, "a flat nib's paint is an ellipse"); near(r.body.impasto, .6, "impasto"); near(r.body.tilt, .7996911196911198, "tilt");
        near(r.body.tDark, .5001929011195422, "tDark"); near(r.body.streaks, .6996138996138996, "streaks"); near(r.windowLen, 103.98455598455597, "windowLen");
        near(r.look.cover, 1, "look cover"); near(r.look.impasto, .6, "look impasto"); near(r.look.gran, .5003861003861004, "look gran");
    }
    static void coal() {
        ResolvedBrush r = r("soft-coal", 16);
        check(r.rule == ResolvedBrush.Rule.CHARCOAL && r.line && r.body == null && r.pen.fast && r.life == ResolvedBrush.Life.NONE, "Charcoal is a line, final as laid");
        near(r.inkK, .8041081081081081, "inkK"); near(r.pen.fuzz, .3498069498069498, "fuzz"); near(r.pen.tooth, .8, "tooth"); near(r.pen.tiltSize, .7996911196911198, "tiltSize");
        ResolvedBrush big = r("soft-coal", 360);
        near(big.inkSize, 240, "Charcoal caps at 240 px"); near(big.washSize, 360, "washSize"); near(big.bodySize, 528, "bodySize"); near(big.windowLen, 192, "windowLen");
    }
    static void hake() {
        ResolvedBrush r = r("wide-hake", 16);
        check(r.lineTip == ResolvedBrush.LineTip.HAKE && !r.line && r.body != null && r.body.flatHead && r.body.headFixed && !r.body.wet && r.body.fast, "A fixed hake laying flat wash");
        near(r.body.streaks, .3498069498069498, "streaks"); near(r.body.pressure, .19992277992277996, "pressure");
    }
    static void rake() {
        ResolvedBrush r = r("rake", 16);
        check(r.pen.lines == ResolvedBrush.Lines.SPREAD && r.pen.count == 4 && r.linePlaced && r.lineReach == 375, "A 4-line rake, placed");
        near(r.pen.skipEnd, 11.536572220824544, "Offset's skips come from the seed"); near(r.body.skipStart, 8.251397428451753, "body skipStart");
        near(r.parts.lineWet, 1.5717683397683397, "lineWet"); near(r.parts.bodyAmt, .7755472592592593, "bodyAmt"); near(r.parts.diffuse, .96, "diffuse");
        check(r.parts.diffR == 10 && r.parts.placed && r.parts.stampLine, "diffR");
        near(r.inkK, .805349806949807, "inkK"); near(r.placeSize, 121.64694980694982, "placeSize"); near(r.body.amount, .7755472592592593, "amount");
        near(r.body.soft, .5464, "soft"); near(r.body.pWidth, .45001923048255094, "pWidth"); near(r.look.feather, .288, "feather");
        near(r.pen.speed, -.3998455598455599, "speed"); near(r.pen.pDark, .3002701211967621, "pDark");
        ResolvedBrush big = r("rake", 360);
        check(big.lineReach == 5966, "lineReach at 360: " + big.lineReach); near(big.placeSize, 2737.056370656371, "placeSize at 360"); near(big.bodySize, 580.8581700188381, "bodySize at 360");
    }
    static void pencil() {
        ResolvedBrush r = r("hb-pencil", 16);
        check(r.rule == ResolvedBrush.Rule.GRAPHITE && r.body == null, "Graphite is a line");
        near(r.inkK, .5590430766901355, "inkK"); near(r.pen.pDark, .5997683397683398, "pDark"); near(r.pen.tooth, .6996138996138996, "tooth");
    }
    static void fallbacks() {
        ModularBrush bristles = ModularCode.decode("test-tip.Md6rog7js5ei0_000zz0000000000000_0_000_0_0000000000000000000nz0.9ix");
        ResolvedBrush r = ResolvedBrush.of(bristles, 16, 1);
        ModularBrush.Part tip = bristles.part(BrushPart.TIP);
        check(r.lineTip == ResolvedBrush.LineTip.BRISTLES && r.bodyTip == ResolvedBrush.BodyTip.BRISTLES && r.lineStateful && r.bodyStateful && !r.linePlaced,
                "Bristles draw as the swarm, line and body (parts.js lineTip, bodyTip)");
        check(r.pen.dry == 0 && r.lineSwarm.seed == bristles.seed && r.lineSwarm.hairs == tip.get(PartDial.HAIRS) && r.lineSwarm.splay == tip.get(PartDial.SPLAY)
                && r.lineSwarm.cohesion == tip.get(PartDial.COHESION) && r.lineSwarm.dryness == tip.get(PartDial.DRYNESS) && r.lineSwarm.amount == 1
                && r.lineSwarm.load == PartDial.INK_LOAD.def, "its swarm reads the tip's dials, Ink load at its default; no dry brush of a nib's");
        ModularBrush wash = bristles.copy(); wash.set(BrushPart.MEDIUM, new ModularBrush.Part(PartChoice.WASH));
        ResolvedBrush w = ResolvedBrush.of(wash, 60, 1);
        near(w.bodySwarm.amount, w.parts.bodyAmt, "A bristle wash's body swarm lays the body's amount");
        ModularBrush drag = ModularCode.decode("test-carry.Md0i000000000at_000zz0000000000000_2llp6ul_000_0_0000000000000000000nz0.9ix");
        ModularBrush off = drag.copy(); off.set(BrushPart.CARRY, new ModularBrush.Part(PartChoice.OFF));
        // Smudge as the lab resolves it (parts.js resolve, after the Smudge spec's F1 and F4): Own paint scales the ink, a
        // dab as wide as the mark to 180 px.
        ResolvedBrush d16 = ResolvedBrush.of(drag, 16, 1), d360 = ResolvedBrush.of(drag, 360, 1), o16 = ResolvedBrush.of(off, 16, 1);
        near(d16.inkK, 0.8501930501930502, "Own paint scales the ink"); near(o16.inkK, 1, "Off lays all its own paint"); check(o16.carry == null, "Off has no smudge");
        near(d16.carry.r, 8.003088803088804, "r"); near(d16.carry.spacing, 1.6402471042471043, "spacing"); near(d16.carry.hits, 4.87919703218274, "hits");
        check(d16.carry.n == 10 && d16.carry.stride == 1 && d16.carry.moves && d16.carry.kind == PartChoice.DRAG, "cells");
        near(d360.carry.r, 180, "r caps at 180 px"); near(d360.carry.spacing, 15.4, "spacing"); check(d360.carry.n == 31 && d360.carry.stride == 6, "at most 63 cells a side");
    }
    static void near(double got, double want, String what) { check(Math.abs(got - want) <= 1e-9 * Math.max(1, Math.abs(want)), what + ": " + got + " vs " + want); }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
