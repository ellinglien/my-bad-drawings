package io.github.mpdairy.monopaint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Fridge magnets ({@link Magnets}, {@link MagnetDrag}): the tile's size, its bounds and what the pen can hold; placing
 * keeps a tile on the page; picking one up brings it to the front, lifting it over the bin or off the page takes it off;
 * Undo and Redo take each step back, redrawing only where magnets changed; the PNG copy gets them drawn over the
 * drawing; and a drag's outline, timed.
 */
public final class MagnetChecks {
    static final int W = 1920, H = 2400;

    public static void main(String[] args) {
        tile(); model(); placement(); drag(); undo(); stamp(); redrawCost();
        System.out.println("PASS: fridge magnets: Paintmageddon's tiles, " + String.format("%.1f", Magnets.EM / Magnets.MM) + " mm Roboto Mono words on "
                + String.format("%.1f", Magnets.HEIGHT / Magnets.MM) + " mm tiles (one letter square), their bounds holding tile, shadow and tilt; "
                + "the topmost under the pen picked, the bin open or closed (a finger never); placed and moved tiles kept wholly on the page; a tile picked up comes to the front, lifted over the bin or off the page "
                + "comes off, barely moved stays; Undo and Redo take back placing, moving and removing, redrawing only the tiles' places; the PNG copy draws them over the drawing; "
                + "a moving tile shows as its outline, four bands (" + drawnPerMove + " px a move, " + String.format("%.4f", drawnShare * 100) + "% of the page, " + String.format("%.3f", msPerMove) + " ms a move on the desktop)");
    }

    static Magnets.Magnet m(String word, int x, int y) { return new Magnets.Magnet(word, x, y, 0, 0); }

    static void tile() {
        float em = Magnets.EM / Magnets.MM, tall = Magnets.HEIGHT / Magnets.MM;
        check(em >= 2.5 && em <= 3, "Words 2.5 to 3 mm, about half the first tiles' 5 mm (" + em + " mm)");
        // Paintmageddon's tile: 13 px words, 3 px above and below the line, 7 px each side.
        check(Math.abs(Magnets.PAD / (float) Magnets.EM - 7 / 13f) < .03, "The room each side, Paintmageddon's 7 px at 13 px words");
        float line = 1.319f * Magnets.EM, above = (Magnets.HEIGHT - line) / 2;
        check(Math.abs(above / Magnets.EM - 3 / 13f) < .03, "The room above and below the word's line, Paintmageddon's 3 px at 13 px words (" + above + " px)");
        check(tall >= 4 && tall <= 5, "Tiles 4 to 5 mm tall, still easy for the pen (" + tall + " mm)");
        check(Magnets.BASELINE - .711f * Magnets.EM > 0 && Magnets.BASELINE + .271f * Magnets.EM < Magnets.HEIGHT
                && Math.abs((Magnets.BASELINE - .711f * Magnets.EM / 2) - Magnets.HEIGHT / 2f) < 2, "The word's capitals and baseline centred on the tile, its descenders inside it");
        check(Magnets.SHADOW >= 1 && Magnets.SHADOW <= 2 && Magnets.CORNER <= 2 && Magnets.LINE == 1, "A 1 to 2 px shadow, nearly square corners, a fine edge");
        check(Magnets.width("s") == Magnets.HEIGHT && Magnets.width("a") == Magnets.HEIGHT, "A one-letter tile is square");
        check(Magnets.width("whisper") > Magnets.width("moon") && Magnets.width("whisper") % 2 == 0, "Longer words, wider tiles, in whole pairs");
        check(Magnets.width("mmmm") == Magnets.width("iiii") && Magnets.width("\u00e9t\u00e9") == Magnets.width("ete"), "Roboto Mono: every letter as wide, accents too");
        check(Magnets.width("\u65e5\u672c\u8a9e") > Magnets.width("abc"), "A sign Roboto Mono lacks gets a fallback face's room");
        int[] wide = Magnets.face(m("whisper", 600, 600)), onEnd = Magnets.face(new Magnets.Magnet("whisper", 600, 600, 12, 1));
        check(wide[2] - wide[0] == Magnets.width("whisper") && onEnd[2] - onEnd[0] == Magnets.HEIGHT && onEnd[3] - onEnd[1] == Magnets.width("whisper"), "A tile's face, untilted, on end at a quarter turn");
        for (int tilt = -Magnets.MOST_TILT; tilt <= Magnets.MOST_TILT; tilt += 5)
            for (int turn = 0; turn < 4; turn++) {
                Magnets.Magnet t = new Magnets.Magnet("languid", 500, 500, tilt, turn);
                int[] b = Magnets.bounds(t);
                double a = Math.toRadians(Magnets.degrees(tilt, turn));
                double hw = Magnets.width("languid") / 2.0, hh = Magnets.HEIGHT / 2.0;
                for (double[] p : new double[][]{{-hw, -hh}, {hw + Magnets.SHADOW, hh + Magnets.SHADOW}, {hw + Magnets.SHADOW, -hh}, {-hw, hh + Magnets.SHADOW}}) {
                    double x = 500 + p[0] * Math.cos(a) - p[1] * Math.sin(a), y = 500 + p[0] * Math.sin(a) + p[1] * Math.cos(a);
                    check(x > b[0] && x < b[2] && y > b[1] && y < b[3], "Bounds hold the tile and its shadow at tilt " + tilt + ", turn " + turn);
                }
                check(Magnets.holds(t, 500, 500) && !Magnets.holds(t, b[0] - 1, b[1] - 1), "The pen holds a tile by its face");
            }
        Magnets.Magnet up = new Magnets.Magnet("whisper", 600, 600, 0, 1);
        check(Magnets.holds(up, 600, 600 + Magnets.width("whisper") / 2 - 4) && !Magnets.holds(up, 600 + Magnets.width("whisper") / 2 - 4, 600), "A quarter-turned tile stands on end");
    }
    static void model() {
        Magnets a = Magnets.NONE.with(m("the", 300, 300)).with(m("moon", 370, 330)).with(m("sings", 900, 900));
        check(a.size() == 3 && a.hit(400, 330) == 1 && a.hit(330, 320) == 1 && a.hit(270, 280) == 0 && a.hit(10, 10) == -1, "The topmost tile under the pen");
        Magnets b = a.moved(0, m("the", 300, 300));
        check(b.get(2).word.equals("the") && b.hit(330, 320) == 2, "Picked up, a tile comes to the front");
        check(Magnets.changed(a, b).isEmpty(), "Only coming to the front changes no pixels a step must redraw (a moved tile is new)");
        Magnets c = b.without(2);
        check(c.size() == 2 && c.hit(270, 280) == -1, "Taken off");
        List<int[]> moved = Magnets.changed(a, a.moved(2, m("sings", 100, 2000)));
        check(moved.size() == 2, "A moved tile changes its old and its new place, and nothing between");
        check(Magnets.of(new ArrayList<>(a.list())).equals(a) && !a.equals(b), "Magnets compare by their tiles and order");
        Magnets full = Magnets.NONE;
        for (int i = 0; i < Magnets.MOST + 5; i++) full = full.with(m("a", 100, 100));
        check(full.size() == Magnets.MOST, "At most " + Magnets.MOST + " magnets a page");
        check(!Magnets.validWord("") && !Magnets.validWord("two words") && !Magnets.validWord("abcdefghijklmnopqrstuvwxyz") && Magnets.validWord("&") && Magnets.validWord("don't"), "Words a tile holds");
    }
    static void placement() {
        Random random = new Random(7);
        for (int i = 0; i < 2000; i++) {
            String word = i % 3 == 0 ? "s" : i % 3 == 1 ? "symphony" : "apparatus";
            Magnets.Magnet raw = new Magnets.Magnet(word, random.nextInt(W + 400) - 200, random.nextInt(H + 400) - 200,
                    random.nextInt(2 * Magnets.MOST_TILT + 1) - Magnets.MOST_TILT, random.nextInt(4));
            int[] b = Magnets.bounds(Magnets.inside(raw, W, H));
            check(b[0] >= 0 && b[1] >= 0 && b[2] <= W && b[3] <= H, "A placed tile lies wholly on the page: " + Arrays.toString(b));
        }
        Magnets.Magnet inner = m("moon", 900, 900);
        check(Magnets.inside(inner, W, H).equals(inner), "A tile already on the page stays where it is");
        Magnets.Magnet small = Magnets.inside(m("apparatus", 0, 0), 120, 60);
        check(small.x == 60 && small.y == 30, "A page too small for a tile centres it");
    }
    static void drag() {
        Magnets page = Magnets.NONE.with(m("the", 300, 300)).with(m("moon", 800, 800));
        // Picked up and moved: on top, where the pen left it, its hold kept.
        MagnetDrag d = new MagnetDrag(page, 0, page.get(0), 310, 305);
        check(!d.moveTo(312, 306), "A pen barely moving hasn't moved the tile");
        check(d.moveTo(510, 405) && d.at().x == 500 && d.at().y == 400, "The tile follows the pen where it was held");
        Magnets after = d.lift(510, 405, W, H, false);
        check(after.size() == 2 && after.get(1).word.equals("the") && after.get(1).x == 500, "Lifted: the tile stays there, on top");
        // Barely moved: nothing changes, not even the order.
        MagnetDrag still = new MagnetDrag(page, 0, page.get(0), 300, 300);
        check(still.lift(304, 303, W, H, false) == page, "Tapped, a tile stays as it was");
        // Over the bin, or off the page: taken off.
        check(new MagnetDrag(page, 1, page.get(1), 800, 800).lift(800, 2300, W, H, true).size() == 1, "Lifted over the bin, it comes off");
        check(new MagnetDrag(page, 1, page.get(1), 800, 800).lift(-20, 900, W, H, false).size() == 1, "Lifted off the page, it comes off");
        // Dragged to an edge: moved fully onto the page.
        Magnets edge = new MagnetDrag(page, 1, page.get(1), 800, 800).lift(1919, 5, W, H, false);
        int[] b = Magnets.bounds(edge.get(1));
        check(b[0] >= 0 && b[1] >= 0 && b[2] <= W && b[3] <= H, "Lifted at the edge, it lies wholly on the page");
        // A new tile from the bin: placed where the pen lands, or carried on.
        Magnets.Magnet fresh = new Magnets.Magnet("whisper", 1000, 1000, 12, 0);
        check(new MagnetDrag(page, -1, fresh, 1000, 1000).lift(1000, 1000, W, H, false).size() == 3, "A word from the bin is placed with a tap");
        check(new MagnetDrag(page, -1, fresh, 1000, 1000).lift(1000, 2300, W, H, true) == page, "A word carried back into the bin isn't placed");
        // Who a landing belongs to: the pen on a magnet moves it, the bin open or closed (the owner's "can't move them", 2026-10-19).
        check(MagnetDrag.landing(false, true, true, true, false) == MagnetDrag.Landing.MAGNET && MagnetDrag.landing(true, true, true, true, false) == MagnetDrag.Landing.MAGNET,
                "The pen on a magnet picks it up, the bin closed or open");
        check(MagnetDrag.landing(false, true, true, false, false) == MagnetDrag.Landing.DRAWING && MagnetDrag.landing(false, false, true, true, false) == MagnetDrag.Landing.DRAWING
                && MagnetDrag.landing(false, true, false, true, false) == MagnetDrag.Landing.DRAWING, "With the bin closed the pen draws off the magnets, a finger anywhere, and nothing interrupts another gesture");
        check(MagnetDrag.landing(true, true, true, true, true) == MagnetDrag.Landing.NEW && MagnetDrag.landing(true, true, true, false, true) == MagnetDrag.Landing.NEW,
                "A word picked in the bin goes where the pen lands, over a magnet too");
        check(MagnetDrag.landing(true, true, true, false, false) == MagnetDrag.Landing.NOTHING && MagnetDrag.landing(true, false, true, true, true) == MagnetDrag.Landing.NOTHING,
                "With the bin open nothing draws, and a finger moves no magnet");
        // Rectangles.
        int[] a = {0, 0, 100, 100};
        check(MagnetDrag.minus(a, new int[]{0, 60, 100, 100}).size() == 1 && Arrays.equals(MagnetDrag.minus(a, new int[]{0, 60, 100, 100}).get(0), new int[]{0, 0, 100, 60}), "A band along the foot cut off a patch leaves its top");
        check(MagnetDrag.minus(a, new int[]{200, 0, 300, 100}).size() == 1 && MagnetDrag.minus(a, new int[]{-5, -5, 105, 105}).isEmpty(), "A patch clear of the bin is whole; one under it, nothing");
        long sum = 0;
        for (int[] part : MagnetDrag.minus(a, new int[]{30, 30, 60, 60})) sum += MagnetDrag.area(part);
        check(sum == 100 * 100 - 30 * 30, "The parts outside cover the patch less the hole exactly");
    }
    static void undo() {
        ToneDocument page = InkWashLayers.blank(400, 300);
        long revision = page.revision();
        Magnets one = page.magnets().with(m("moon", 100, 100));
        check(page.setMagnets(one) && page.revision() > revision && page.dirty() == null, "Placing is one step; it changes the page's revision, not its paint");
        check(page.takeMagnetAreas().size() == 1 && page.takeMagnetAreas().isEmpty(), "Placing redraws the tile's place, once");
        check(!page.setMagnets(Magnets.of(new ArrayList<>(one.list()))), "The same magnets again are no step");
        Magnets two = new MagnetDrag(one, 0, one.get(0), 100, 100).lift(300, 200, 400, 300, false);
        page.setMagnets(two); page.takeMagnetAreas();
        Magnets three = two.without(0);
        page.setMagnets(three); page.takeMagnetAreas();
        check(page.undo() && page.magnets().equals(two) && page.takeMagnetAreas().size() == 1, "Undo puts a removed tile back");
        check(page.undo() && page.magnets().equals(one) && page.takeMagnetAreas().size() == 2, "Undo moves a tile back, redrawing both places");
        check(page.undo() && page.magnets().isEmpty() && !page.undo(), "Undo takes a placed tile off");
        check(page.redo() && page.redo() && page.redo() && page.magnets().equals(three) && !page.redo(), "Redo does them again");
        // A stroke and magnets share one history.
        page.begin(); page.setTone(10, 10, 0); page.finish();
        page.setMagnets(page.magnets().with(m("ink", 50, 50)));
        check(page.undo() && page.magnets().equals(three) && page.compositeTone(10, 10) == 0, "Undo takes the last step, a magnet's, first");
        check(page.undo() && page.compositeTone(10, 10) == 255 && page.magnets().equals(three), "then the stroke under it");
        // A snapshot carries them; a page from it too.
        page.redo(); page.redo();
        ToneDocument copy = new ToneDocument(page.layerSnapshot());
        check(copy.magnets().equals(page.magnets()) && !copy.canUndo(), "A snapshot's page carries its magnets, no history");
    }
    /** Solid tiles: ink face, a one-pixel paper rim, transparent round it; enough to see where the copy draws. */
    static final Magnets.Tiles BLOCKS = m -> {
        int[] e = Magnets.extent(m.word, m.tilt, m.turn);
        int w = e[2] - e[0], h = e[3] - e[1];
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            boolean on = Magnets.holds(m, m.x + e[0] + x, m.y + e[1] + y);
            px[y * w + x] = on ? 0xff000000 : 0;
        }
        return new Magnets.Tile(px, w, h);
    };
    static void stamp() {
        ToneDocument page = InkWashLayers.blank(600, 400);
        page.setMagnets(page.magnets().with(m("moon", 200, 150)).with(new Magnets.Magnet("whisper", 590, 395, 20, 0)));
        int[] before = page.exportPixels(true), after = before.clone();
        Magnets.stamp(after, 600, 400, page.magnets(), BLOCKS);
        int changed = 0, outside = 0;
        int[] b0 = Magnets.bounds(page.magnets().get(0)), b1 = Magnets.bounds(page.magnets().get(1));
        for (int y = 0; y < 400; y++) for (int x = 0; x < 600; x++) {
            if (after[y * 600 + x] == before[y * 600 + x]) continue;
            changed++;
            boolean in = x >= b0[0] && x < b0[2] && y >= b0[1] && y < b0[3] || x >= b1[0] && x < b1[2] && y >= b1[1] && y < b1[3];
            if (!in) outside++;
        }
        check(after[150 * 600 + 200] == 0xff000000 && changed > Magnets.width("moon") * Magnets.HEIGHT / 2 && outside == 0, "The PNG copy draws each magnet in its place, nowhere else (" + changed + " px)");
        check(after.length == before.length, "and a tile hanging off the picture is clipped");
        int[] grey = {0xff808080};
        int[] e = Magnets.extent("a", 0, 0);
        Magnets.stamp(grey, 1, 1, Magnets.NONE.with(m("a", -e[0], -e[1])), x -> new Magnets.Tile(new int[]{0x80ffffff}, 1, 1));
        check((grey[0] & 255) > 0xb0 && (grey[0] & 255) < 0xd0, "A tile's antialiased edge is laid over what is under it");
    }
    static long drawnPerMove; static double drawnShare, msPerMove;
    /**
     * A drag's outline ({@link MagnetDrag#outline}): four bands round the tile's face that never overlap and lie in it;
     * each move puts back the bands' pixels where it was (saved) and draws them at its new place, as WordsMode does on
     * the tablet; only those bands reach the panel, while the tile stays where it was until the pen lifts.
     */
    static void redrawCost() {
        for (int turn = 0; turn < 4; turn++) {
            Magnets.Magnet t = new Magnets.Magnet("symphony", 700, 700, 20, turn);
            int[] f = Magnets.face(t);
            List<int[]> bands = MagnetDrag.outline(t);
            long sum = 0;
            for (int i = 0; i < bands.size(); i++) {
                int[] r = bands.get(i);
                check(r[0] >= f[0] && r[1] >= f[1] && r[2] <= f[2] && r[3] <= f[3] && r[2] > r[0] && r[3] > r[1], "The outline lies on the face's edge");
                for (int j = 0; j < i; j++) { int[] o = bands.get(j); check(r[0] >= o[2] || r[2] <= o[0] || r[1] >= o[3] || r[3] <= o[1], "Its bands never overlap"); }
                sum += MagnetDrag.area(r);
            }
            int k = MagnetDrag.OUTLINE;
            check(sum == MagnetDrag.area(f) - MagnetDrag.area(new int[]{f[0] + k, f[1] + k, f[2] - k, f[3] - k}), "The bands make the whole ring, " + k + " px wide, at turn " + turn);
        }
        int[] pagePx = new int[W * H];
        Arrays.fill(pagePx, 0xffffffff);
        Magnets.Magnet held = new Magnets.Magnet("symphony", 600, 600, 15, 0);
        int moves = 400;
        long drawn = 0;
        long start = System.nanoTime();
        List<int[]> at = MagnetDrag.outline(held);
        List<int[]> saved = new ArrayList<>();
        for (int[] r : at) { saved.add(save(pagePx, r)); fill(pagePx, r); }
        for (int i = 1; i <= moves; i++) {
            List<int[]> to = MagnetDrag.outline(held.at(600 + i * 3, 600 + i * 2));
            for (int b = at.size() - 1; b >= 0; b--) restore(pagePx, at.get(b), saved.get(b));
            saved.clear();
            for (int[] r : to) { saved.add(save(pagePx, r)); fill(pagePx, r); drawn += 2 * MagnetDrag.area(r); }
            at = to;
        }
        double ms = (System.nanoTime() - start) / 1e6 / moves;
        drawnPerMove = drawn / moves; drawnShare = drawnPerMove / (double) (W * H); msPerMove = ms;
        check(drawnShare < .001, "A move redraws under 0.1% of the page (" + drawnShare + ")");
        Timing.limit(ms < 5, "A move's redraw takes under 5 ms on the desktop (" + ms + ")");
        for (int b = at.size() - 1; b >= 0; b--) restore(pagePx, at.get(b), saved.get(b));
        for (int p : pagePx) check(p == 0xffffffff, "Put back, the page without the outline is as it was");
    }
    private static int[] save(int[] page, int[] r) {
        int w = r[2] - r[0]; int[] out = new int[w * (r[3] - r[1])];
        for (int y = r[1]; y < r[3]; y++) System.arraycopy(page, y * W + r[0], out, (y - r[1]) * w, w);
        return out;
    }
    private static void restore(int[] page, int[] r, int[] in) { int w = r[2] - r[0]; for (int y = r[1]; y < r[3]; y++) System.arraycopy(in, (y - r[1]) * w, page, y * W + r[0], w); }
    private static void fill(int[] page, int[] r) { for (int y = r[1]; y < r[3]; y++) Arrays.fill(page, y * W + r[0], y * W + r[2], 0xff000000); }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
