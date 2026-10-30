package io.github.mpdairy.monopaint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An old Wild brush's style, as its {@code I}/{@code P} share code carries it: an index into its kind's list. Pens:
 * Fineliner, Brush pen, Dip pen, Marker, Pencil, Charcoal; paints: Watercolor, Ink wash, Gouache, Oils, Pastels, Dry
 * brush; and Wild for each. A converted brush takes the six-part style of the same name (Wild is Anything goes,
 * {@link LegacyBrushes}). The ranges each style rolled within went with the old lab. Never reorder a kind's styles.
 * Pure, checked on the desktop.
 */
final class BrushStyle {
    static final String WILD = "Wild";
    final WildBrush.Kind kind;
    final String name;
    /** Its place in its kind's list, as share codes carry it. */
    final int index;

    private BrushStyle(WildBrush.Kind kind, String name, int index) { this.kind = kind; this.name = name; this.index = index; }

    private static final List<BrushStyle> INK = list(WildBrush.Kind.INK, "Fineliner", "Brush pen", "Dip pen", "Marker", "Pencil", "Charcoal", WILD);
    private static final List<BrushStyle> PAINT = list(WildBrush.Kind.PAINT, "Watercolor", "Ink wash", "Gouache", "Oils", "Pastels", "Dry brush", WILD);

    /** A kind's styles, Wild last, in share-code order. */
    static List<BrushStyle> of(WildBrush.Kind kind) { return kind == WildBrush.Kind.INK ? INK : PAINT; }
    static BrushStyle wild(WildBrush.Kind kind) { List<BrushStyle> list = of(kind); return list.get(list.size() - 1); }
    static BrushStyle named(WildBrush.Kind kind, String name) {
        for (BrushStyle style : of(kind)) if (style.name.equals(name)) return style;
        throw new IllegalArgumentException("No " + kind + " style " + name);
    }
    boolean wild() { return name.equals(WILD); }
    /** A shaped nib's width range: chisels are broader than flat nibs. */
    static double[] nibWidths(WildBrush.Nib nib) { return nib == WildBrush.Nib.CHISEL ? new double[]{.3, .55} : new double[]{.14, .28}; }

    private static List<BrushStyle> list(WildBrush.Kind kind, String... names) {
        List<BrushStyle> list = new ArrayList<>();
        for (String name : names) list.add(new BrushStyle(kind, name, list.size()));
        return Collections.unmodifiableList(list);
    }
}
