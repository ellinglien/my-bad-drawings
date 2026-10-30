package io.github.mpdairy.monopaint;

import java.util.Random;

public final class BrushCodeChecks {
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        mockup(); roundTrip(); roundNib(); speedAtZero(); bad(); slug(); identity();
        System.out.println("PASS: share codes are the mockup's (name slug, kind, style, two base-36 digits a dial, seed), read back to the same brush within half a step, round nibs and speed's direction at 0 included; bad codes read as nothing, a code in capitals reads; a brush is its settings and seed, not its name");
    }
    static WildBrush rolled(WildBrush.Kind kind, long seed, String name) { WildBrush b = WildBrush.roll(kind, seed); b.name = name; return b; }
    static void mockup() {
        check(BrushCode.encode(rolled(WildBrush.Kind.INK, 505, "dapper-fig")).equals("dapper-fig.I610gavij3f7q16o0000006m00.e1"), "Seed 505's pen, as the mockup writes it");
        check(BrushCode.encode(rolled(WildBrush.Kind.INK, 1, "Bobson Dugnutt")).equals("bobson-dugnutt.I610zkyum2fbzsge00ej5k00e7.1"), "Seed 1's pen, the name as a slug");
        check(BrushCode.encode(rolled(WildBrush.Kind.PAINT, 625, "mellow-plum")).equals("mellow-plum.P600xjf400c4jzxh00aqhu.hd"), "Seed 625's paint");
        check(BrushCode.encode(rolled(WildBrush.Kind.PAINT, 77, "bouncy-moose")).equals("bouncy-moose.P610hh46000000000000wp.25"), "Seed 77's flat paint");
        WildBrush felt = rolled(WildBrush.Kind.INK, 1, "x"); felt.texture = BrushTexture.FELT;
        check(BrushCode.encode(felt).equals("x.I613zkyum2fbzsge00ej5k00e7.1"), "A texture is one digit");
    }
    static void roundTrip() {
        Random random = new Random(7);
        for (int i = 0; i < 2000; i++) {
            WildBrush.Kind kind = i % 2 == 0 ? WildBrush.Kind.INK : WildBrush.Kind.PAINT;
            java.util.List<BrushStyle> styles = BrushStyle.of(kind);
            WildBrush b = WildBrush.roll(kind, random.nextInt() & 0xffffffffL);
            b.style = styles.get(random.nextInt(styles.size()));
            b.texture = BrushTexture.values()[random.nextInt(BrushTexture.LEGACY)];
            String code = BrushCode.encode(b);
            WildBrush back = BrushCode.decode(code);
            check(back != null && back.kind == b.kind && back.style == b.style && back.seed == b.seed && back.name.equals(BrushCode.slug(b.name)), "A code reads back: " + code);
            for (BrushSetting s : BrushSetting.of(kind)) {
                if (!s.choice() && !s.active(b)) continue;
                // Speed's two middle steps read as 0, keeping only its direction: a full step there.
                double step = (s.max - s.min) / BrushCode.STEPS, within = s == BrushSetting.SPEED ? step : step / 2;
                check(s.choice() ? s.get(back) == s.get(b) : Math.abs(s.get(back) - s.get(b)) <= within + 1e-9,
                        s.label + " reads back within half a step: " + code);
            }
            check(BrushCode.encode(back).equals(code), "A code read back writes the same code");
        }
    }
    static void roundNib() {
        WildBrush b = rolled(WildBrush.Kind.INK, 1, "round"); b.nib = WildBrush.Nib.ROUND; b.normalize();
        String code = BrushCode.encode(b);
        WildBrush back = BrushCode.decode(code);
        check(back != null && back.nib == WildBrush.Nib.ROUND && back.aspect == 1, "A round nib's code reads back round (the mockup's didn't): " + code);
    }
    static void speedAtZero() {
        for (boolean thicker : new boolean[]{false, true}) {
            WildBrush b = rolled(WildBrush.Kind.INK, 1, "still"); b.speed = 0; b.thickerWhenFast = thicker;
            WildBrush back = BrushCode.decode(BrushCode.encode(b));
            check(back.speed == 0 && back.speedDirection() == (thicker ? 1 : -1), "Speed at 0 keeps which way it works: " + (thicker ? "thicker" : "thinner"));
        }
    }
    static void bad() {
        String good = BrushCode.encode(rolled(WildBrush.Kind.INK, 505, "dapper-fig"));
        String[] codes = {null, "", "hello", "dapper-fig", "dapper-fig.I610gavij3f7q16o0000006m00", good + "x.", good.replace(".I6", ".I9"),
                good.replace(".I610", ".I640"), good.replace("6m00.", "6m0."), good.replace("6m00.", "6m000."), good.replace(".e1", ".zzzzzzz"),
                good.replace(".I6", ".X6"), "dapper fig.I610gavij3f7q16o0000006m00.e1"};
        for (String code : codes) check(BrushCode.decode(code) == null, "A bad code reads as nothing: " + code);
        check(BrushCode.decode("  " + good + "\n") != null, "Spaces around a pasted code don't matter");
        for (String shouted : new String[]{good.toUpperCase(), "Dapper-Fig.I610gavij3f7q16o0000006m00.e1", good.replace(".I6", ".i6")}) {
            WildBrush back = BrushCode.decode(shouted);
            check(back != null && BrushCode.encode(back).equals(good), "A code in other case reads as the same brush: " + shouted);
        }
    }
    static void slug() {
        check(BrushCode.slug("Bobson Dugnutt").equals("bobson-dugnutt") && BrushCode.slug("my-ink").equals("my-ink")
                && BrushCode.slug(" ??3 ").equals("brush") && BrushCode.slug("Ünïcode café").equals("n-code-caf"), "Names become lowercase slugs");
    }
    static void identity() {
        WildBrush a = rolled(WildBrush.Kind.PAINT, 3_300_000_001L, "a"), b = a.copy();
        b.name = "b"; b.style = BrushStyle.named(WildBrush.Kind.PAINT, "Oils");
        check(a.key().equals(b.key()) && a.key().equals(a.copy().key()), "A brush's key is its settings and seed, not its name or style");
        WildBrush c = a.copy(); c.gran = a.gran < .5 ? .9 : .1;
        WildBrush d = a.copy(); d.texture = BrushTexture.LAID;
        check(!c.key().equals(a.key()) && !d.key().equals(a.key()), "Other settings or another texture are another brush");
        check(!a.key().equals(a.copy(5).key()), "Another seed is another brush");
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
