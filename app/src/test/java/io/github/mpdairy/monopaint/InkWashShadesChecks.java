package io.github.mpdairy.monopaint;

public final class InkWashShadesChecks {
    public static void main(String[] args) {
        InkWashShades.Shade[] all = InkWashShades.Shade.values();
        check(all.length == 4 && all[0] == InkWashShades.Shade.DARK && all[1] == InkWashShades.Shade.MID && all[2] == InkWashShades.Shade.LIGHT
                && all[3] == InkWashShades.Shade.LIGHTER, "Four shades per tool, dark to lighter, the new Mid second");
        check(InkWashShades.Shade.saved("MID") == InkWashShades.Shade.MID && InkWashShades.ink(InkWashShades.Shade.MID) == 60
                && InkWashShades.wash(InkWashShades.Shade.MID) == 218 && Math.abs(InkWashShades.watercolor(InkWashShades.Shade.MID) - .78f) < 1e-6,
                "Mid halves the widest step: ink 60 (black to 120), wash 218, watercolor .78");
        check(InkWashShades.Shade.saved(null) == InkWashShades.Shade.DARK && InkWashShades.Shade.saved("PURPLE") == InkWashShades.Shade.DARK,
                "Nothing or nonsense saved is the dark shade");
        check(InkWashShades.Shade.saved("LIGHT") == InkWashShades.Shade.LIGHT && InkWashShades.Shade.saved("DARK") == InkWashShades.Shade.DARK,
                "Shades saved before the third keep their meaning");
        check(InkWashShades.Shade.saved("LIGHTER") == InkWashShades.Shade.LIGHTER, "A saved lighter shade loads");

        check(InkWashShades.ink(InkWashShades.Shade.DARK) == 0, "Ink is black");
        check(InkWashShades.ink(InkWashShades.Shade.LIGHT) == 120, "or grey, about 120");
        check(InkWashShades.ink(InkWashShades.Shade.LIGHTER) == 175, "or light grey, about 175");
        check(InkWashShades.wash(InkWashShades.Shade.DARK) == 205, "Wash dark is about 205, lighter than the first version's 196");
        check(InkWashShades.wash(InkWashShades.Shade.LIGHT) == 230, "Wash light is about 230");
        check(InkWashShades.wash(InkWashShades.Shade.LIGHTER) == 245, "Wash lighter is about 245");
        check(InkWashShades.watercolor(InkWashShades.Shade.DARK) == 1, "Deep watercolor gets as dark as watercolor gets");
        check(Math.abs(InkWashShades.watercolor(InkWashShades.Shade.LIGHT) - .55f) < 1e-6, "Pale watercolor gets about .55 as dark");
        check(Math.abs(InkWashShades.watercolor(InkWashShades.Shade.LIGHTER) - .3f) < 1e-6, "Paler watercolor gets about .3 as dark");
        for (int i = 1; i < all.length; i++)
            check(InkWashShades.ink(all[i]) > InkWashShades.ink(all[i - 1]) && InkWashShades.wash(all[i]) > InkWashShades.wash(all[i - 1])
                    && InkWashShades.watercolor(all[i]) < InkWashShades.watercolor(all[i - 1]), "Each shade is lighter than the one before, for every tool");
        System.out.println("PASS: four shades, dark by default, saved names keep their meaning; the old tools' greys (ink 0/120/175, wash 205/230/245, watercolor pale at .55 and paler at .3), which converted slots keep, and Mid between dark and light (ink 60, wash 218, watercolor .78)");
    }
    private static void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
}
