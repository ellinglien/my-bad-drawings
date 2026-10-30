package io.github.mpdairy.monopaint;

public final class BrushChecks {
    public static void main(String[] args) {
        kinds(); stored(); old(); same(); clean();
        System.out.println("PASS: a brush is a six-part brush, stored as its name and M code; its medium says where it draws; built-ins (@INK) and old I/P lines read as their six-part brushes, names kept; lines that don't read are nothing");
    }
    static ModularBrush roll(String style, long seed) { return ModularStyle.named(style).roll(new java.util.Random(seed)); }
    static void kinds() {
        check(Brush.of(roll("Fineliner", 1)).inks() && Brush.of(roll("Pencil", 1)).inks() && Brush.of(roll("Charcoal", 1)).inks(), "Ink, graphite and charcoal draw on the ink layer");
        check(!Brush.of(roll("Watercolor", 1)).inks() && !Brush.of(roll("Gouache", 1)).inks(), "Wash and cover on the wash layer");
        ModularBrush b = roll("Marker", 2); b.name = "Sleek Pear";
        check(Brush.of(b).name().equals("Sleek Pear"), "Its name is the six-part brush's");
    }
    static void stored() {
        ModularBrush b = roll("Dip pen", 3); b.name = "Bobson Dugnutt";
        String text = Brush.of(b).write();
        check(text.equals("Bobson Dugnutt\t" + ModularCode.encode(b)), "Stored as its name and M code: " + text);
        Brush back = Brush.read(text);
        check(back.name().equals("Bobson Dugnutt") && back.parts.key().equals(b.key()) && back.parts.style == b.style, "and read back with its real name");
        for (String bad : new String[]{null, "", "@PENCIL", "@", "name only", "x\tnot-a-code", "x\tbrave-onion.M9z0000.1"}) check(Brush.read(bad) == null, "Unreadable: " + bad);
    }
    /** Lines stored before the six-part engine read as their six-part brushes (LegacyConversionChecks pins the codes). */
    static void old() {
        check(Brush.read("@INK").name().equals("Fineliner") && Brush.read("@FUDE").name().equals("Brush pen") && Brush.read("@WASH").name().equals("Ink wash")
                && Brush.read("@WATERCOLOR").name().equals("Watercolour"), "A built-in reads as its six-part brush, under its new name");
        Brush pen = Brush.read("Bobson Dugnutt\tbobson-dugnutt.I610zkyum2fbzsge00ej5k00e7.1");
        check(pen != null && pen.name().equals("Bobson Dugnutt") && pen.inks() && pen.parts.style.wild, "An old Wild pen reads as six parts, its name kept, Anything goes");
        check(Brush.read(pen.write()).sameAs(pen) && pen.write().contains(".M"), "and is stored as an M code from then on");
        Brush paint = Brush.read("mellow plum\tmellow-plum.P600xjf400c4jzxh00aqhu.hd");
        check(paint != null && !paint.inks() && paint.parts.choice(BrushPart.MEDIUM) == PartChoice.WASH, "An old Wild paint reads as a wash");
        check(Brush.read("x\tdapper-fig.I610gavij3f7q16o0000006m0.e1") == null, "An old code that doesn't read is nothing");
    }
    static void same() {
        ModularBrush b = roll("Fineliner", 4);
        check(Brush.of(b).sameAs(Brush.of(b.copy())), "The same name and settings are the same brush");
        ModularBrush renamed = b.copy(); renamed.name = "other";
        check(!Brush.of(b).sameAs(Brush.of(renamed)) && !Brush.of(b).sameAs(Brush.of(b.copy(b.seed + 1))), "another name or seed is another");
        check(!Brush.of(b).sameAs(null), "Nothing is no brush");
    }
    static void clean() {
        check(Brush.clean("  a\tb\n  c ").equals("a b c") && Brush.clean("x".concat(new String(new char[40]).replace('\0', 'y'))).length() == Brush.NAME_MAX, "Names are one line, at most 32 characters");
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
