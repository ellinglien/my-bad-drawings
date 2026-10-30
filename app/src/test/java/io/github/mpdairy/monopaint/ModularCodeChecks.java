package io.github.mpdairy.monopaint;

import java.util.Locale;
import java.util.Random;

public final class ModularCodeChecks {
    /** Codes the prototype wrote (modular-lab-src, encodeM). The app must read them and write them back unchanged. */
    static final String BRAVE_ONION = "brave-onion.M24p6okati000ee00_0atzz9000000020000_0_277_1e0_6ee00ee0000we000000nz0.1u5hvr4";
    static final String[] FIXTURES = {
            "wet-wash.M60i000000000ee_1wei000ee7700_0_1at_2i0ll0js_1ss0000000000000000nz0.2r",
            "thick-oil.M925aeei00000p600at_2zzll_0_5i0_1rz_5ee0000ssi000000000nz0.7",
            "soft-coal.M51vleei000000000at_3clat_0_1ss_0_8p60000ssi000000000nz0.3",
            "wide-hake.Mc3i01zzcl77_1eeat00770000_0_000_0_8770000000000000000nz0.5",
            "rake.Md5nzssi000000000_0llp6000000000at77_0_000_0_4weatg7000000ee0000nz0.b",
            "hb-pencil.M40i000000000at_4i000_0_2p6_0_8atll00000000000000nz0.2",
            BRAVE_ONION};
    /** A fixture brush by its name, decoded afresh. */
    static ModularBrush fixture(String name) {
        for (String code : FIXTURES) if (code.startsWith(name + ".")) return ModularCode.decode(code);
        throw new IllegalArgumentException(name);
    }

    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        golden(); handBuilt(); fixtures(); shortCodes(); bad(); roundTrip(); identity(); added(); wet();
        System.out.println("PASS: M share codes are the prototype's byte for byte (style, each part's choice and dials, seed), read back within half a step, read short codes as defaults and any case; bad codes and I/P codes read as nothing; a brush's key is its parts and seed; Tilt range, which the prototype lacks, is written only when wide; Wet is Drying's eighth choice, its Stays Wet written only when off its default, and no older code holds it");
    }
    static void golden() {
        String[][] fresh = {
                {"TIP", "HAKE", "test-tip.Md3i00zzcl77_000zz0000000000000_0_000_0_0000000000000000000nz0.9ix"},
                {"TIP", "SPLIT_NIB", "test-tip.Md4jsokeei0000000_000zz0000000000000_0_000_0_0000000000000000000nz0.9ix"},
                {"TIP", "MULTI_LINE", "test-tip.Md5c0lli000000000_000zz0000000000000_0_000_0_0000000000000000000nz0.9ix"},
                {"TIP", "BRISTLES", "test-tip.Md6rog7js5ei0_000zz0000000000000_0_000_0_0000000000000000000nz0.9ix"},
                {"MEDIUM", "WASH", "test-medium.Md0i000000000at_1ulat00770000_0_000_0_0000000000000000000nz0.9ix"},
                {"MEDIUM", "COVER", "test-medium.Md0i000000000at_2zz00_0_000_0_0000000000000000000nz0.9ix"},
                {"MEDIUM", "CHARCOAL", "test-medium.Md0i000000000at_3atee_0_000_0_0000000000000000000nz0.9ix"},
                {"MEDIUM", "GRAPHITE", "test-medium.Md0i000000000at_4ee00_0_000_0_0000000000000000000nz0.9ix"},
                {"CARRY", "DRAG", "test-carry.Md0i000000000at_000zz0000000000000_2llp6ul_000_0_0000000000000000000nz0.9ix"},
                {"PAPER", "CANVAS", "test-paper.Md0i000000000at_000zz0000000000000_0_5i0_0_0000000000000000000nz0.9ix"},
                {"LIFE", "FAST_WET", "test-life.Md0i000000000at_000zz0000000000000_0_000_1jz_0000000000000000000nz0.9ix"},
                {"LIFE", "DRYING", "test-life.Md0i000000000at_000zz0000000000000_0_000_2clcl0js_0000000000000000000nz0.9ix"},
                {"HAND", "BRUSH_HAND", "test-hand.Md0i000000000at_000zz0000000000000_0_000_0_4weatg7000000ee0000nz0.9ix"},
                {"HAND", "QUICK_THIN", "test-hand.Md0i000000000at_000zz0000000000000_0_000_0_7at0000000000p6i0eenz0.9ix"},
                {"HAND", "CUSTOM", "test-hand.Md0i000000000at_000zz0000000000000_0_000_0_8000000000000000000nz0.9ix"}};
        for (String[] f : fresh) {
            ModularBrush b = new ModularBrush("test-" + f[0].toLowerCase(Locale.ROOT), 12345, ModularStyle.named("Anything goes"));
            b.set(BrushPart.valueOf(f[0]), new ModularBrush.Part(PartChoice.valueOf(f[1])));
            check(ModularCode.encode(b).equals(f[2]), "A fresh " + f[1] + " writes as the prototype's: " + ModularCode.encode(b));
        }
    }
    static void handBuilt() {
        ModularBrush b = new ModularBrush("Brave Onion", 4_000_000_000L, ModularStyle.named("Dip pen"));
        ModularBrush.Part tip = new ModularBrush.Part(PartChoice.SPLIT_NIB); tip.set(PartDial.GAP, .7); tip.set(PartDial.ANGLE, 35); tip.set(PartDial.TAPER_OUT, .4);
        ModularBrush.Part medium = new ModularBrush.Part(PartChoice.INK); medium.set(PartDial.WET, .3); medium.set(PartDial.LINES, 2); medium.set(PartDial.POOL, .25);
        ModularBrush.Part paper = new ModularBrush.Part(PartChoice.LAID); paper.set(PartDial.TOOTH, .2);
        ModularBrush.Part life = new ModularBrush.Part(PartChoice.FAST_WET); life.set(PartDial.WINDOW, 4.5);
        b.set(BrushPart.TIP, tip); b.set(BrushPart.MEDIUM, medium); b.set(BrushPart.PAPER, paper); b.set(BrushPart.LIFE, life);
        b.set(BrushPart.HAND, new ModularBrush.Part(PartChoice.TILT_NIB));
        check(ModularCode.encode(b.normalize()).equals(BRAVE_ONION), "A brush built by hand writes the prototype's code: " + ModularCode.encode(b));
    }
    static void fixtures() {
        for (String code : FIXTURES) {
            ModularBrush b = ModularCode.decode(code);
            check(b != null && ModularCode.encode(b).equals(code), "The prototype's code reads and writes back unchanged: " + code);
        }
        ModularBrush onion = fixture("brave-onion");
        check(onion.name.equals("brave-onion") && onion.seed == 4_000_000_000L && onion.style.name.equals("Dip pen") && onion.choice(BrushPart.TIP) == PartChoice.SPLIT_NIB
                && onion.part(BrushPart.MEDIUM).get(PartDial.LINES) == 2 && Math.abs(onion.part(BrushPart.LIFE).get(PartDial.WINDOW) - 4.5) < 9 / 1295.0 / 2 + 1e-9, "brave-onion's parts");
        ModularBrush wash = fixture("wet-wash");
        check(wash.style.name.equals("Watercolor") && wash.choice(BrushPart.MEDIUM) == PartChoice.WASH && wash.choice(BrushPart.PAPER) == PartChoice.COLD_PRESS
                && wash.choice(BrushPart.LIFE) == PartChoice.DRYING && wash.part(BrushPart.LIFE).get(PartDial.SETTLE) == 777 / 1295.0 && wash.choice(BrushPart.HAND) == PartChoice.PRESS_WIDER, "wet-wash's parts");
        check(fixture("wide-hake").part(BrushPart.TIP).get(PartDial.HEAD_TURN) == 1 && fixture("rake").part(BrushPart.TIP).get(PartDial.COUNT) == 4
                && fixture("thick-oil").choice(BrushPart.MEDIUM) == PartChoice.COVER && fixture("soft-coal").choice(BrushPart.HAND) == PartChoice.CUSTOM, "The others' parts");
    }
    static void shortCodes() {
        String full = "a.Md0i000000000at_000zz0000000000000_0_000_0_0000000000000000000nz0.1";
        check(ModularCode.encode(ModularCode.decode("a.Md0_0_0_0_0_0.1")).equals(full), "A code that stops early reads the rest as defaults");
        check(ModularCode.encode(ModularCode.decode("A.MD0_0_0_0_0_0.1")).equals(full), "A code in capitals reads");
        check(ModularCode.encode(ModularCode.decode("a.Md0_0_0_0_0_0_5.1")).equals(full), "Segments past the sixth are ignored");
        check(ModularCode.encode(ModularCode.decode("a.Md0_0_0_0_0_6.1")).equals("a.Md0i000000000at_000zz0000000000000_0_000_0_6ee00ee0000we000000nz0.1"), "A hand preset without dials takes its cells");
        check(ModularCode.decode("  " + BRAVE_ONION + "\n") != null, "Spaces around a pasted code don't matter");
    }
    static void bad() {
        String[] codes = {null, "", "hello", "a.Mf0_0_0_0_0_0.1", "a.Mdd_0_0_0_0_0.1", "a.Md3i02_0_0_0_0_0.1", "a.Md0_0_0_0_0_0.zzzzzzz", "a.Md0_0_0_0_0.1",
                "x.M0_0_0.1", "a.M9z0000.1", "a.Md0_0_0_0_0_0", "a b.Md0_0_0_0_0_0.1", "dapper-fig.I610gavij3f7q16o0000006m00.e1"};
        for (String code : codes) check(ModularCode.decode(code) == null, "A bad code, or an I/P code, reads as no M brush: " + code);
        check(BrushCode.decode("dapper-fig.I610gavij3f7q16o0000006m00.e1") != null, "I/P codes still read as before");
    }
    /** Tilt range: last of the hand's dials; a code without it reads normal, and a normal brush's code is the prototype's. */
    static void added() {
        String old = "brush-pen.M10at00000000at_000zz0000000000000_0_000_0_8at0000zz00zz000000nz0.c";
        ModularBrush b = ModularCode.decode(old);
        check(b.part(BrushPart.HAND).get(PartDial.TILT_RANGE) == 0 && ModularCode.encode(b).equals(old), "A code without Tilt range reads normal and writes as it was");
        b.part(BrushPart.HAND).set(PartDial.TILT_RANGE, 1);
        String wide = ModularCode.encode(b);
        check(wide.equals(old.replace("nz0.c", "nz01.c")) && ModularCode.decode(wide).part(BrushPart.HAND).get(PartDial.TILT_RANGE) == 1, "A wide one writes it at the hand's end and reads it back: " + wide);
        check(!b.key().equals(ModularCode.decode(old).key()), "and draws differently, so its key differs");
    }
    static void roundTrip() {
        Random random = new Random(9);
        for (int i = 0; i < 2000; i++) {
            ModularBrush b = ModularStyle.at(i % ModularStyle.all().size()).roll(random);
            String code = ModularCode.encode(b);
            ModularBrush back = ModularCode.decode(code);
            check(back != null && ModularCode.encode(back).equals(code) && back.seed == b.seed && back.style == b.style && back.name.equals(BrushCode.slug(b.name)), "A code reads back: " + code);
            for (BrushPart part : BrushPart.values()) {
                check(back.choice(part) == b.choice(part), "Choices read back: " + code);
                for (PartDial d : b.part(part).choice.dials) {
                    double within = d.choice() || d.integer ? 0 : (d.max - d.min) / 1295 / 2 + 1e-9;
                    check(Math.abs(back.part(part).get(d) - b.part(part).get(d)) <= within, d + " reads back within half a step: " + code);
                }
            }
        }
    }
    static void identity() {
        ModularBrush a = fixture("rake"), b = new ModularBrush("other", a.seed, ModularStyle.named("Oils"));
        for (BrushPart p : BrushPart.values()) b.set(p, a.part(p).copy());
        check(a.key().equals(b.key()) && a.key().equals(ModularCode.settings(a)) && !a.key().contains("rake"), "A brush's key is its parts and seed, not its name or style");
        check(!a.key().equals(a.copy(5).key()), "Another seed is another brush");
    }
    /**
     * Wet (the wet paint spec): Drying's eighth choice, index 7, which no older code could hold (its digit read as no
     * brush before it); a Wet brush's code writes its Stays Wet only off its default, then Runs Downhill, and reads back
     * as itself. Every fixture and Basic tool reads and writes as before (none is Wet but the Wet presets).
     */
    static void wet() {
        check(PartChoice.of(BrushPart.LIFE, 7) == PartChoice.WET_PAINT && BrushPart.LIFE.choices().size() == 8, "Wet is Drying's eighth choice, appended");
        ModularBrush b = fixture("thick-oil").copy();
        b.set(BrushPart.LIFE, new ModularBrush.Part(PartChoice.WET_PAINT));
        String plain = ModularCode.encode(b);
        check(plain.contains("_7_"), "Wet at its defaults writes the choice alone: " + plain);
        b.part(BrushPart.LIFE).set(PartDial.STAYS_WET, .8); b.part(BrushPart.LIFE).set(PartDial.RUNS, .3);
        String code = ModularCode.encode(b);
        ModularBrush back = ModularCode.decode(code);
        check(back != null && back.choice(BrushPart.LIFE) == PartChoice.WET_PAINT && ModularCode.encode(back).equals(code)
                && Math.abs(back.part(BrushPart.LIFE).get(PartDial.STAYS_WET) - .8) < .001 && Math.abs(back.part(BrushPart.LIFE).get(PartDial.RUNS) - .3) < .001, "A Wet code round-trips: " + code);
        for (String f : FIXTURES) check(ModularCode.decode(f).choice(BrushPart.LIFE) != PartChoice.WET_PAINT && ModularCode.encode(ModularCode.decode(f)).equals(f), "An older code reads and writes as before: " + f);
        for (String[] row : ToolPresets.ROWS) {
            ModularBrush p = ModularCode.decode(row[2]);
            check(ModularCode.encode(p).equals(row[2]) || HandHatchChecks.handHatch(row[2]), row[1] + "'s code reads and writes as before (Hand Hatch's reads as Hatch)");
        }
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
