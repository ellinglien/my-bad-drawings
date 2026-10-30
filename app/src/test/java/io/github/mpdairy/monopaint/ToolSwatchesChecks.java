package io.github.mpdairy.monopaint;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public final class ToolSwatchesChecks {
    static Map<String, String> prefs = new HashMap<>();
    static BrushLibrary.Store store() {
        return new BrushLibrary.Store() {
            public String get(String key) { return prefs.get(key); }
            public void put(String key, String value) { if (value == null) prefs.remove(key); else prefs.put(key, value); }
        };
    }
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        defaults(); untouched(); choose(); eraser(); picks(); slider(); stored(); fourth(); washBlack(); columnSlider(); sliderSlots(); dropper(); everyTool();
        System.out.println("PASS: four size and four grey swatches; each tool's own size and grey (2026-10-27), set by a swatch picked or set and by the slider, kept through picks, rolls, Undo and a restart, another tool's change leaving it, a notebook's toolbar bringing its own; the eraser's no finer than twice its tip; "
                + "each tool's own size and grey as an older build kept them still read (three swatches kept by an older build keep their values, order and marks, the fourth in the widest step until it is set); "
                + "since washes reach black, a grey kept before draws a wash at the glaze it laid (an ink as it was), written nothing; a grey picked since is the grey bar's for every medium; "
                + "the side column's size slider (2026-10-24) maps every size to its place and back and gives the fine sizes room; the Dropper picks the grey the page shows over every layer and plane, no guides, and sets only the tool's grey; "
                + "a grey is the tool's alone, kept in range");
    }
    static Brush style(String name, long seed) { return Brush.of(ModularStyle.named(name).roll(new Random(seed))); }
    static boolean near(double a, double b) { return Math.abs(a - b) < 1e-9; }

    static void defaults() {
        prefs = new HashMap<>();
        ToolSwatches s = new ToolSwatches(store());
        check(ToolSwatches.COUNT == 4 && ToolSwatches.SIZES.length == 4 && ToolSwatches.GREYS.length == 4, "Four of each");
        check(s.size(0) == 4 && s.size(1) == 16 && s.size(2) == 44 && s.size(3) == 120, "Sizes start at 4, 16, 44 and 120 px");
        check(String.format(Locale.ROOT, "%.3f %.3f %.3f %.3f", s.grey(0), s.grey(1), s.grey(2), s.grey(3)).equals("1.000 0.765 0.529 0.314"),
                "Greys start at Dark, Mid, Light and Lighter: 1, .765, .529, .314");
        Brush pen = style("Fineliner", 1);
        check(s.sizeInUse("slot0") == -1 && !prefs.containsKey(ToolSwatches.PX + "slot0"), "No size of its own until one is set");
        check(s.look("slot0", pen).px == ModularSizes.size(pen.parts, InkWashSizes.Preset.MEDIUM) && s.look("slot0", pen).darkness == 1,
                "Until then a tool with nothing stored draws at M and Dark");
    }
    /** A six-part slot whose size and shade were stored before swatches draws as it did. */
    static void untouched() {
        prefs = new HashMap<>();
        prefs.put("inkwash_size_slot2", "LARGE"); prefs.put("inkwash_shade_slot2", "LIGHT"); prefs.put("inkwash_size_slot3", "EXTRA_SMALL");
        ToolSwatches s = new ToolSwatches(store());
        Brush pen = style("Fineliner", 1);
        ToolSwatches.Look look = s.own("slot2", pen);
        check(look.preset == InkWashSizes.Preset.LARGE && s.look("slot2", pen).px == look.px && look.px == ToolSwatches.diameter(pen, InkWashSizes.Preset.LARGE)
                && look.px == ModularSizes.size(pen.parts, InkWashSizes.Preset.LARGE) && near(look.darkness, ModularSizes.darkness(InkWashShades.Shade.LIGHT)),
                "A six-part slot with no px or darkness yet draws at its preset's size and its shade's darkness: " + look.px);
        check(s.own("slot3", pen).preset == InkWashSizes.Preset.SMALL, "The retired extra small reads as S");
        check(prefs.size() == 3, "Reading stores nothing");
    }
    /** Each tool's own (2026-10-27): a swatch picked or set is the tool's size alone. */
    static void choose() {
        prefs = new HashMap<>();
        ToolSwatches s = new ToolSwatches(store());
        Brush pen = style("Fineliner", 1), wash = style("Watercolor", 2);
        double before = s.look("slot5", wash).px;
        s.chooseSize("slot0", 3);
        check(s.look("slot0", pen).px == 120 && s.sizeInUse("slot0") == 3 && s.look("slot5", wash).px == before && s.sizeInUse("slot5") == -1, "Picking size 4 sets 120 px for that tool alone");
        s.setSize("slot1", 0, 7);
        check(s.size(0) == 7 && s.sizeInUse("slot1") == 0 && s.look("slot1", pen).px == 7 && s.look("slot0", pen).px == 120, "Setting size 1 to 7 px sets the swatch and the tool's size");
        s.setSize("slot1", 0, 1000);
        check(s.size(0) == 360, "Sizes stop at 360 px");
        s.setSize("slot1", 0, .5);
        check(s.size(0) == 1.5 && s.look("slot1", pen).px == 1.5, "and 1.5");
        s.useGrey("slot0", .6);
        check("0.6".equals(prefs.get(ToolSwatches.DARK + "slot0")) && s.look("slot0", pen).darkness == .6 && s.look("slot5", wash).darkness != .6
                && s.look(ToolSlots.STAMP_KEY, pen).darkness != .6, "A grey is the tool's alone");
        s.useGrey("slot0", -1);
        check(s.look("slot0", pen).darkness == 0, "Greys stop at 0");
        s.useGrey("slot0", 2);
        check(s.look("slot0", pen).darkness == 1 && s.grey(0) == 1 && s.grey(1) == ToolSwatches.GREYS[1], "and 1; the grey swatches as they were");
    }
    /**
     * The eraser and the smudge tool keep their own size, as Procreate's (the owner, 2026-10-25: "eraser keeps its own
     * size i think.. whatever the procreate convention is"), as every tool since 2026-10-27. The eraser never finer than
     * twice its tip.
     */
    static void eraser() {
        prefs = new HashMap<>();
        ToolSwatches s = new ToolSwatches(store());
        Brush pen = style("Fineliner", 1), stump = ToolSlots.firstSmudge();
        s.useSize("slot0", 30);
        s.useSize(ToolSwatches.ERASER_KEY, 57);
        s.useSize(ToolSlots.SMUDGE_KEY, 33);
        check(s.look(ToolSwatches.ERASER_KEY, null).px == 57 && s.look(ToolSlots.SMUDGE_KEY, stump).px == 33 && s.look("slot0", pen).px == 30 && s.look("slot4", pen).px != 30,
                "The eraser at its own 57 px, the smudge tool at its own 33, slot 0 at its 30");
        s.useSize("slot2", 12); s.chooseSize("slot0", 3);
        check(s.look(ToolSwatches.ERASER_KEY, null).px == 57 && s.look(ToolSlots.SMUDGE_KEY, stump).px == 33 && s.look("slot0", pen).px == 120, "A brush's size leaves theirs");
        s.useSize(ToolSwatches.ERASER_KEY, 1);
        check(s.look(ToolSwatches.ERASER_KEY, null).px == ToolSwatches.ERASER_LEAST && s.look("slot0", pen).px == 120, "The eraser never finer than twice its tip, the brushes untouched");
    }
    /**
     * Picks keep the slot's size (since 2026-10-25; until then a brush of the other family took the size nearest its own):
     * a pick, a roll, Undo and a kit leave every slot's size as it was.
     */
    static void picks() {
        prefs = new HashMap<>();
        ToolSlots slots = new ToolSlots(store(), new Random(1));
        ToolSwatches s = slots.swatches;
        Brush fineliner = style("Fineliner", 1), watercolor = style("Watercolor", 2);
        check(fineliner.inks() && !watercolor.inks(), "(fixtures: a pen and a paint)");
        slots.use(0, fineliner);
        s.chooseSize("slot0", 0);
        slots.use(0, watercolor);
        check(s.look("slot0", watercolor).px == 4, "A Fineliner's 4 px slot given a Watercolor stays at 4 px");
        s.useSize("slot0", 9);
        slots.undo();
        check(slots.brush(0).sameAs(fineliner) && s.look("slot0", fineliner).px == 9, "Undo takes the brush back, not the size");
        double[] px = new double[ToolSlots.COUNT];
        for (int i = 0; i < ToolSlots.COUNT; i++) px[i] = s.look(ToolSlots.key(i), slots.brush(i)).px;
        slots.roll(1);
        slots.useAll(new Brush[]{style("Gouache", 4), style("Charcoal", 5)});
        for (int i = 0; i < ToolSlots.COUNT; i++) check(s.look(ToolSlots.key(i), slots.brush(i)).px == px[i], "A roll and a kit keep the size: slot " + i);
    }
    static double nearest(ToolSwatches s, double px) {
        double best = s.size(0);
        for (int i = 1; i < ToolSwatches.COUNT; i++) if (Math.abs(Math.log(s.size(i) / px)) < Math.abs(Math.log(best / px))) best = s.size(i);
        return best;
    }
    static void slider() {
        for (double stop : ToolSwatches.STOPS) check(Math.abs(ToolSwatches.sizeAt(ToolSwatches.position(stop)) - stop) < 1e-9, "The slider finds stop " + stop);
        check(ToolSwatches.position(1.5) == 0 && ToolSwatches.position(360) == 1 && ToolSwatches.sizeAt(0) == 1.5 && ToolSwatches.sizeAt(1) == 360, "It runs from 1.5 to 360 px");
        check(ToolSwatches.mm(16).equals("1.4 mm") && ToolSwatches.mm(240).equals("20 mm"), "Sizes in millimetres: " + ToolSwatches.mm(16) + ", " + ToolSwatches.mm(240));
    }
    static void stored() {
        prefs = new HashMap<>();
        prefs.put(ToolSwatches.SIZE_SWATCHES, "4,x,120"); prefs.put(ToolSwatches.GREY_SWATCHES, "1,.5");
        prefs.put("inkwash_px_slot0", "junk"); prefs.put("inkwash_grey_slot0", "NaN");
        ToolSwatches s = new ToolSwatches(store());
        Brush pen = style("Fineliner", 1);
        check(s.size(1) == 16 && s.grey(1) == ToolSwatches.GREYS[1], "Unreadable triples are the defaults");
        prefs.put(ToolSwatches.SIZE_SWATCHES, "0.2,16,9000"); prefs.put(ToolSwatches.GREY_SWATCHES, "-1,.5,7");
        check(s.size(0) == ModularSizes.MIN && s.size(3) == ModularSizes.MAX && s.grey(0) == 0 && s.grey(2) == .5 && s.grey(3) == 1, "Stored swatches out of range read as the nearest in range");
        prefs.remove(ToolSwatches.SIZE_SWATCHES); prefs.remove(ToolSwatches.GREY_SWATCHES);
        check(s.look("slot0", pen).px == ModularSizes.size(pen.parts, InkWashSizes.Preset.MEDIUM) && s.look("slot0", pen).darkness == 1, "and an unreadable px or darkness what the preset and shade drew");
        Locale was = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            s.setSize("slot0", 0, 2.5); s.useGrey("slot0", .6);
            check("2.5,16,120".equals(prefs.get(ToolSwatches.SIZE_SWATCHES)) && "0.6".equals(prefs.get(ToolSwatches.DARK + "slot0"))
                    && ToolSwatches.mm(16).equals("1.4 mm"), "Written with a '.' in any language: " + prefs.get(ToolSwatches.SIZE_SWATCHES) + " " + prefs.get(ToolSwatches.DARK + "slot0"));
        } finally { Locale.setDefault(was); }
        check(new ToolSwatches(store()).size(0) == 2.5 && new ToolSwatches(store()).look("slot0", pen).px == 2.5, "and read back");
    }
    /**
     * Four since 2026-10-19: what an older build kept (three sizes and three greys, the tools' marks by name) keeps its
     * values, its order and its marks; the fourth sits in the widest step until it is set, and the older three stay
     * where an older build reads them.
     */
    static void fourth() {
        prefs = new HashMap<>();
        prefs.put(ToolSwatches.SIZE_SWATCHES, "6,20,200"); prefs.put(ToolSwatches.GREY_SWATCHES, "1,0.6,0.2");
        prefs.put("inkwash_size_slot0", "LARGE"); prefs.put("inkwash_shade_slot0", "LIGHT");
        prefs.put("inkwash_size_slot1", "MEDIUM"); prefs.put("inkwash_shade_slot1", "LIGHTER");
        prefs.put("inkwash_size_ERASER", "LARGE");
        Map<String, String> before = new HashMap<>(prefs);
        ToolSwatches s = new ToolSwatches(store());
        Brush pen = style("Fineliner", 1);
        check(s.size(0) == 6 && s.size(1) == 20 && s.size(3) == 200 && s.grey(0) == 1 && s.grey(2) == .6 && s.grey(3) == .2,
                "An older build's three sizes and greys keep their values and order");
        check(s.size(2) == 63 && near(s.grey(1), .8), "the fourth size between M and the old L on a log scale (√(20 × 200) = 63), the fourth grey halfway from Dark to Light");
        check(s.own("slot0", pen).preset == InkWashSizes.Preset.LARGE && near(s.own("slot0", pen).darkness, .6) && s.own("slot1", pen).preset == InkWashSizes.Preset.MEDIUM
                && near(s.own("slot1", pen).darkness, .2), "Each tool's own swatches, as an older build kept them, read at the values they had");
        check(s.own("ERASER", null).preset == InkWashSizes.Preset.LARGE && s.look("ERASER", null).px == 220, "The eraser's old L is its XL, 220 as before");
        check(prefs.equals(before), "Reading writes nothing");
        // The defaults an older build kept are the defaults now.
        prefs = new HashMap<>();
        prefs.put(ToolSwatches.SIZE_SWATCHES, "4,16,120"); prefs.put(ToolSwatches.GREY_SWATCHES, "1," + (1 - 120 / 255.0) + "," + (1 - 175 / 255.0));
        check(s.size(2) == ToolSwatches.SIZES[2] && near(s.grey(1), ToolSwatches.GREYS[1]), "The old defaults' fourths are the new defaults: 44 px and Mid");
        // Setting any swatch writes the fourth, so it stays put when its neighbours change; the older three stay as an older build reads them.
        prefs = new HashMap<>(before);
        s.setSize("slot0", 0, 5);
        check("5,20,200".equals(prefs.get(ToolSwatches.SIZE_SWATCHES)) && "63".equals(prefs.get(ToolSwatches.SIZE_FOURTH)), "Setting a size writes the three as before and the fourth: "
                + prefs.get(ToolSwatches.SIZE_SWATCHES) + " " + prefs.get(ToolSwatches.SIZE_FOURTH));
        s.setSize("slot0", 1, 40);
        check(s.size(2) == 63, "and the fourth stays put after");
        s.setSize("slot0", 2, 80);
        check(s.size(2) == 80 && "5,40,200".equals(prefs.get(ToolSwatches.SIZE_SWATCHES)) && s.look("slot0", pen).px == 80,
                "Setting the fourth leaves the three an older build reads");
        prefs.put(ToolSwatches.SIZE_FOURTH, "junk");
        check(s.size(2) == ToolSwatches.sizeAt(ToolSwatches.position(Math.sqrt(40 * 200))), "An unreadable fourth sits between its neighbours");
        prefs.put(ToolSwatches.SIZE_FOURTH, "9000");
        check(s.size(2) == ModularSizes.MAX, "a fourth out of range reads as the nearest in range");
    }
    /**
     * Washes reach black (2026-10-19): a tool's darkness kept before (inkwash_grey_) is left as it is and read as it drew,
     * an ink at it and a wash at what lays the glaze it laid ({@link ModularSizes#sinceBlack}), so every slot and the
     * smudge tool look as they did; a grey picked since (inkwash_dark_) is the grey bar's for every medium. Once: nothing
     * is rewritten, so a launch cut short, a second launch or an older build all read the same.
     */
    static void washBlack() {
        Brush wash = style("Watercolor", 2), pen = style("Fineliner", 1);
        check(ModularSizes.washes(wash.parts) && !ModularSizes.washes(pen.parts), "(fixtures: a wash and a pen)");
        prefs = new HashMap<>();
        prefs.put("inkwash_grey_slot0", "1"); prefs.put("inkwash_grey_slot1", "0.53"); prefs.put("inkwash_shade_slot1", "LIGHT");
        Map<String, String> before = new HashMap<>(prefs);
        ToolSwatches s = new ToolSwatches(store());
        for (String key : new String[]{"slot0", "slot1"}) {
            double old = Double.parseDouble(before.get("inkwash_grey_" + key)), now = s.look(key, wash).darkness;
            ResolvedBrush r = ResolvedBrush.of(wash.parts, 120, now);
            check(near(now, old * ModularSizes.GLAZE / ModularSizes.WASH_BLACK) && Math.abs(r.washK - ModularSizes.GLAZE * old) < 1e-12
                            && ModularSizes.swatch(wash.parts, now) == (int) Math.round(255 * (1 - ModularSizes.GLAZE * old)),
                    "A wash kept at " + old + " draws the glaze it laid, " + Math.round(100 * ModularSizes.GLAZE * old) + "%: " + now + " lays " + r.washK);
            check(s.look(key, pen).darkness == old, "an ink in the same slot at the grey itself");
        }
        check(prefs.equals(before), "Reading writes nothing: a launch cut short, a second one and an older build read the same");
        // The smudge tool has no greys: at its swatch's darkness, a wash's as it laid it.
        check(near(s.look(ToolSlots.SMUDGE_KEY, wash).darkness, ModularSizes.sinceBlack(wash.parts, s.grey(0))) && s.look(ToolSlots.SMUDGE_KEY, pen).darkness == s.grey(0),
                "The smudge tool, with no grey of its own, draws a wash as it did");
        // A grey picked since: the bar's, for every medium; the one kept before stays as it was.
        s.useGrey("slot0", 1);
        check(s.look("slot0", wash).darkness == 1 && s.look("slot0", pen).darkness == 1 && "1".equals(prefs.get(ToolSwatches.DARK + "slot0"))
                        && "1".equals(prefs.get("inkwash_grey_slot0")) && ModularSizes.swatch(wash.parts, 1) == (int) Math.round(255 * (1 - ModularSizes.WASH_BLACK)),
                "Picking black since: the wash at black, its glaze " + Math.round(100 * ModularSizes.WASH_BLACK) + "%, the ink black");
        s.useGrey("slot1", .4);
        check(s.look("slot1", wash).darkness == .4 && "0.4".equals(prefs.get(ToolSwatches.DARK + "slot1")), "Setting a grey since: the bar's");
        // The mapping: light washes lay as ever, black near black; every darkness the same lay over and over.
        for (double d = 0; d <= 1.0001; d += .05) {
            double k = ModularSizes.washStrength(d), laid = ModularSizes.laid(k);
            check(Math.abs(laid - ModularSizes.WASH_BLACK * Math.min(1, d)) < 1e-9 && (k == ModularSizes.WASH_BLACK * Math.min(1, d) || laid > ModularSizes.KNEE),
                    "A full wash body at " + d + " lays " + ModularSizes.WASH_BLACK + " of it: " + laid);
        }
        for (double x = 0; x < 3; x += .01) {
            check(ModularSizes.laid(x + .01) > ModularSizes.laid(x) && ModularSizes.laid(x) < 1 && (x > ModularSizes.KNEE || ModularSizes.laid(x) == x)
                    && Math.abs(ModularSizes.strength(ModularSizes.laid(x)) - x) < 1e-9, "Darker with strength, never quite black, as ever to the knee: " + x);
        }
        check(ModularSizes.GLAZE * 1.6 < ModularSizes.KNEE, "Every body laid before washes reached black is under the knee: it lays as it did");
    }

    /** The side column's size slider (the owner, 2026-10-24: "a quick size slider ... to fine tune the size"). */
    static void columnSlider() {
        // Every size the slider sets (half px under 10, whole above) maps to its place and back.
        int n = 0;
        double last = -1;
        for (double px = ModularSizes.MIN; px <= ModularSizes.MAX; px += px < 10 ? .5 : 1, n++) {
            double t = ToolSwatches.position(px);
            check(Math.abs(ToolSwatches.sizeAt(t) - px) < 1e-9 && t > last, "Size " + px + " px to " + t + " and back, each further along");
            last = t;
        }
        check(ToolSwatches.sizeAt(-1) == ModularSizes.MIN && ToolSwatches.sizeAt(2) == ModularSizes.MAX, "Past its ends, the ends: " + n + " sizes");
        // Non-linear: the fine sizes, 1.5 to 10 px (under a millimetre), have a third of the track; on a linear one they'd have 2 %.
        check(ToolSwatches.position(10) > .3, "Fine sizes get room: 10 px at " + ToolSwatches.position(10));
        // The track: a pen's step of a dp moves under 5 % in size, on every screen (6 % until the box grew half again,
        // 2026-10-29), under 4 % on the Manta and the Nomad upright, so fine-tuning is a short drag. Turned, the box only
        // grows into spare room (the Manta's turned had it until Shapes and Play's row, 2026-10-30), under 5 %.
        for (int[] screen : DrawLayoutChecks.SCREENS) {
            DrawLayout d = new DrawLayout(screen[0], screen[1]);
            // The picker box's length (2026-10-29; the four sizes' squares' until then).
            float tall = d.box, track = DrawLayout.trackFoot(tall) - DrawLayout.trackTop();
            double step = Math.pow(ModularSizes.MAX / ModularSizes.MIN, 1 / track) - 1;
            check(track >= 100 && step < .05 && (screen[0] > screen[1] || step < .04), "The slider's track " + track + " dp at " + screen[0] + "x" + screen[1] + ", " + Math.round(1000 * step) / 10.0 + "% a dp");
            // Broad at the top, fine at the foot (the owner, 2026-10-24: "flip the triangle for the size so it goes down").
            check(DrawLayout.sliderAt(DrawLayout.trackTop(), tall) == 1 && DrawLayout.sliderAt(tall, tall) == 0 && DrawLayout.sliderAt(0, tall) == 1
                    && Math.abs(DrawLayout.sliderAt(DrawLayout.sliderY(.37, tall), tall) - .37) < 1e-6, "Its pen's place and its tab's agree");
        }
    }
    /**
     * Each slot's own size and grey, as an older build kept them (faces, or the slider's px), draw as they did; the slider
     * then sets the slot's own size, kept.
     */
    static void sliderSlots() {
        prefs = new HashMap<>();
        Brush pen = style("Fineliner", 1), wash = style("Watercolor", 2);
        prefs.put("inkwash_size_slot0", "LARGER"); prefs.put("inkwash_shade_slot0", "MID");
        prefs.put("inkwash_size_slot1", "LARGE"); prefs.put(ToolSwatches.PX + "slot1", "120");
        Map<String, String> before = new HashMap<>(prefs);
        ToolSwatches s = new ToolSwatches(store());
        ToolSwatches.Look old = s.look("slot0", pen), wet = s.look("slot1", wash);
        check(old.px == (float) ModularSizes.size(pen.parts, InkWashSizes.Preset.LARGER) && near(old.darkness, s.grey(1)) && wet.px == 120 && prefs.equals(before),
                "A slot kept as faces draws at its face's size and grey, one with its own px at that, nothing written: " + old.px + " px");
        check(Math.abs(ToolSwatches.sizeAt(ToolSwatches.position(old.px)) - old.px) < .5, "The slider opens on its size");
        s.useSize("slot1", 23);
        ToolSwatches again = new ToolSwatches(store());
        check(again.look("slot1", wash).px == 23 && again.look("slot0", pen).px == old.px && again.sizeInUse("slot1") == -1 && s.size(1) == 16,
                "The slider's 23 px is the slot's, kept; no face in use, the shared sizes and the other slot unchanged");
        s.useSize("slot1", s.size(2));
        check(s.sizeInUse("slot1") == 2, "At a face's size, that face is in use again");
        s.useSize("slot1", 1000);
        check(s.look("slot1", pen).px == ModularSizes.MAX, "Sizes stop at 360 px");
    }
    /**
     * The Dropper (the owner, 2026-10-24: "a dropper tool so people can quickly match color"): the grey the page shows
     * under the pen, every layer and the stack's planes over the paper, the guides left out; the slot then lays that
     * grey, its size and brush and every other slot untouched, so the tool draws on as it did.
     */
    static void dropper() {
        int w = 8, h = 8, n = w * h;
        ToneDocument page = new ToneDocument(w, h);
        byte[] guides = new byte[n]; java.util.Arrays.fill(guides, (byte) 255); guides[9] = guides[18] = 60;
        page.placeGuides(guides);
        byte[] tones = new byte[n], cover = new byte[n];
        tones[9] = 100; cover[9] = (byte) 128;  // a half-covered mid grey on the template's line
        tones[27] = 0; cover[27] = (byte) 255;  // black
        page.addLayer(tones, cover);
        byte[] under = new byte[n]; under[36] = (byte) 255;  // a layer below, black, through the stack's plane
        byte[] overTones = new byte[n], overAlpha = new byte[n]; overTones[45] = (byte) 200; overAlpha[45] = (byte) 255;  // a layer above
        check(page.shownTone(2, 2, under, 0, overTones, overAlpha) == 255 && page.compositeTone(2, 2) == 60, "The guides are left out: the paper under them");
        check(page.shownTone(1, 1, under, 0, overTones, overAlpha) == (100 * 128 + 255 * 127 + 127) / 255, "A layer over the paper, as the screen lays it, not the guide under it");
        check(page.shownTone(3, 3, under, 0, overTones, overAlpha) == 0 && page.shownTone(4, 4, under, 0, overTones, overAlpha) == 0
                && page.shownTone(5, 5, under, 0, overTones, overAlpha) == 200, "Every layer: this page's, those below and those above");
        check(page.shownTone(3, 3, null, 0, null, null) == page.compositeTone(3, 3), "A page alone, no guides there: its composite");
        page.shadePaper(180);
        check(page.shownTone(18 % w, 18 / w, under, 0, overTones, overAlpha) == 180, "A shaded page's paper");
        prefs = new HashMap<>();
        ToolSwatches s = new ToolSwatches(store());
        Brush pen = style("Fineliner", 1), wash = style("Watercolor", 2);
        s.useSize("slot0", 30); s.useGrey("slot0", .5); s.useGrey("slot1", .5);
        Map<String, String> others = new HashMap<>(prefs);
        for (int tone : new int[]{0, 60, 128, 200, 255}) {
            s.drop("slot0", pen, tone);
            check(ModularSizes.swatch(pen.parts, s.look("slot0", pen).darkness) == tone && s.look("slot0", pen).px == 30, "A pen picks " + tone + " and lays it, its size kept");
            s.drop("slot1", wash, tone);
            int laid = ModularSizes.swatch(wash.parts, s.look("slot1", wash).darkness);
            check(laid == Math.max(tone, (int) Math.round(255 * (1 - ModularSizes.WASH_BLACK))), "A wash picks " + tone + " and lays it, or its own black: " + laid);
        }
        Map<String, String> after = new HashMap<>(prefs);
        for (Map<String, String> m : java.util.Arrays.asList(others, after)) { m.remove(ToolSwatches.DARK + "slot0"); m.remove(ToolSwatches.DARK + "slot1"); }
        check(after.equals(others), "Only the tool's grey changes: sizes, swatches and marks as they were");
    }
    /**
     * Each tool's own (the owner, 2026-10-27: "i think we need per tool memory for size and shade"): every slot keeps the
     * size and grey set on it, whatever is set on another, and a notebook's own toolbar coming and going brings its
     * sizes, then the app's back.
     */
    static void everyTool() {
        prefs = new HashMap<>();
        ToolSlots slots = new ToolSlots(store(), new Random(3));
        ToolSwatches s = slots.swatches;
        double[] px = new double[ToolSlots.COUNT];
        for (int i = 0; i < ToolSlots.COUNT; i++) {
            px[i] = ToolSwatches.sizeAt(.05 + .09 * i);
            s.useSize(ToolSlots.key(i), px[i]); s.useGrey(ToolSlots.key(i), .1 * i);
        }
        s.useSize(ToolSwatches.ERASER_KEY, 27); s.useSize(ToolSlots.SMUDGE_KEY, 57);
        for (int j = 0; j < ToolSlots.COUNT; j++)
            check(s.look(ToolSlots.key(j), slots.brush(j)).px == px[j] && near(s.look(ToolSlots.key(j), slots.brush(j)).darkness, .1 * j), "Slot " + j + " at its own size and grey, " + px[j]);
        check(s.look(ToolSlots.STAMP_KEY, slots.stamp()).px == ToolSlots.FIRST_STAMP_PX, "the stamp tool at its own");
        ToolSlots.Toolbar own = slots.toolbar();
        check(!slots.enter(own) && !slots.leave(), "The slots' own toolbar entering and leaving changes nothing");
        own.px[2] = 200; own.brushes[2] = style("Watercolor", 9);
        check(slots.enter(own) && slots.brush(2).sameAs(own.brushes[2]) && s.look(ToolSlots.key(2), slots.brush(2)).px == 200, "A notebook's own toolbar brings its brushes and sizes");
        check(slots.leave() && s.look(ToolSlots.key(2), slots.brush(2)).px == px[2], "and leaving it brings the app's size back");
        check(s.look(ToolSwatches.ERASER_KEY, null).px == 27 && s.look(ToolSlots.SMUDGE_KEY, slots.smudge()).px == 57, "and the toolbar's coming and going leaves the eraser's and the smudge tool's");
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
