package io.github.mpdairy.monopaint;

public final class WildBrushChecks {
    public static void main(String[] args) {
        generator(); ink(); paint(); budget(); names(); parity();
        System.out.println("PASS: a seed rolls the same name and dials as the desktop roll tester; roughness dials share a budget; flat paint has no hollow edge, bloom or backruns; names are an adjective and an animal; 40,000 rolls draw exactly as before");
    }
    private static void generator() {
        WildBrush.Random r = new WildBrush.Random(1);
        check(near(r.next(), .627073941) && near(r.next(), .002735721) && near(r.next(), .527447040), "rng(1) matches the tester");
    }
    private static void ink() {
        WildBrush b = WildBrush.roll(WildBrush.Kind.INK, 1);
        check(b.name.equals("lumpy-puffin"), "Seed 1 is lumpy-puffin");
        check(b.nib == WildBrush.Nib.CHISEL && near(b.aspect, .545263) && near(b.angle, 68.418895), "Seed 1's nib");
        check(near(b.taperIn, .612839) && near(b.taperOut, .425797) && near(b.pressure, .994823) && near(b.speed, -.089458), "Seed 1's shape dials");
        check(b.dry == 0 && near(b.tooth, .403745) && near(b.wobble, .154493) && b.pool == 0 && near(b.splatter, .394619), "Seed 1's roughness dials");
        WildBrush big = WildBrush.roll(WildBrush.Kind.INK, 4_000_000_000L);
        check(big.name.equals("noodly-hedgehog") && near(big.taperOut, .786852) && near(big.speed, .468139), "A seed above 2^31 rolls as the tester's unsigned seed");
    }
    private static void paint() {
        WildBrush b = WildBrush.roll(WildBrush.Kind.PAINT, 1);
        check(b.name.equals("lumpy-puffin") && b.wet, "Seed 1 paints wet");
        check(near(b.pressure, .981051) && near(b.soft, .968378) && near(b.gran, .612839) && b.feather == 0, "Seed 1's paint dials");
        check(near(b.hollow, .994823) && near(b.bloom, .488788) && near(b.streaks, .403745) && near(b.backrun, .154493), "Seed 1's wet dials");
        check(b.drying >= 0 && b.drying < 1, "Seed 1's drying time");
        WildBrush flat = WildBrush.roll(WildBrush.Kind.PAINT, 77);
        check(flat.name.equals("bouncy-moose") && !flat.wet && flat.hollow == 0 && flat.bloom == 0 && flat.backrun == 0, "Seed 77 is a flat wash");
    }
    private static void budget() {
        for (long seed = 0; seed < 5000; seed++) {
            WildBrush b = WildBrush.roll(WildBrush.Kind.INK, seed);
            check(b.dry + b.tooth + b.wobble <= 1.3 + 1e-9, "Dry, tooth and wobble share a budget of 1.3");
        }
    }
    private static void names() {
        for (long seed = 0; seed < 500; seed++) check(WildBrush.roll(WildBrush.Kind.PAINT, seed).name.matches("[a-z]+-[a-z]+"), "Adjective-animal");
    }
    /** Every dial and name of 40,000 rolls, exactly as the roll tester's port first rolled them: no draw moved or changed. */
    private static void parity() {
        long h = 17;
        for (WildBrush.Kind kind : WildBrush.Kind.values()) for (long seed = 0; seed < 20000; seed++) {
            WildBrush b = WildBrush.roll(kind, seed * 2654435761L);
            for (BrushSetting s : BrushSetting.values()) h = h * 31 + Double.doubleToLongBits(s.get(b));
            h = h * 31 + b.name.hashCode();
        }
        check(h == 0xdedcdd04401ddc60L, "Rolls are bit for bit the same: " + Long.toHexString(h));
    }
    private static boolean near(double a, double b) { return Math.abs(a - b) < 1e-5; }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
