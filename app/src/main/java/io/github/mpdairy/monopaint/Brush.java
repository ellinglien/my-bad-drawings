package io.github.mpdairy.monopaint;

/**
 * What a tool slot or a library entry holds: a six-part brush ({@code M} codes). Its medium decides where it draws
 * (ink, charcoal and graphite on the ink layer, wash and cover on the wash layer), not the slot it sits in. Brushes
 * stored before the six-part engine (a built-in, an {@code I}/{@code P} Wild brush) read as their six-part brushes
 * ({@link LegacyBrushes}). Treat it as a value: to change one, change a copy. Pure, checked on the desktop.
 */
final class Brush {
    /** The longest name a brush keeps. */
    static final int NAME_MAX = 32;
    /** The six-part brush; never null. */
    final ModularBrush parts;

    private Brush(ModularBrush parts) { this.parts = java.util.Objects.requireNonNull(parts, "a brush's parts"); }
    static Brush of(ModularBrush parts) { return new Brush(parts); }

    String name() { return parts.name; }
    /** Whether it draws on the ink layer: ink, charcoal or graphite. Wash and cover paint on the wash layer. */
    boolean inks() { return ModularSizes.inks(parts); }
    /** The same brush: the same name and settings. */
    boolean sameAs(Brush other) { return other != null && identity().equals(other.identity()); }
    /** What {@link #sameAs} compares: its name and its settings' key. */
    String identity() { return parts.name + "\t" + parts.key(); }
    /** As stored: its name and share code, tab-separated. */
    String write() { return clean(parts.name) + "\t" + ModularCode.encode(parts); }
    /** A stored brush, or null if it doesn't read; one stored before the six-part engine reads converted. */
    static Brush read(String text) {
        if (text == null) return null;
        LegacyBrushes.Old old = LegacyBrushes.read(text);
        if (old != null) return of(old.brush);
        int tab = text.indexOf('\t');
        if (tab < 0) return null;
        ModularBrush parts = ModularCode.decode(text.substring(tab + 1));
        if (parts == null) return null;
        String name = clean(text.substring(0, tab));
        // The built-in's old spelling, in Canadian now (the owner, 2026-10-21: Watercolour wherever it is read).
        if (name.equals("Watercolor")) name = "Watercolour";
        if (!name.isEmpty()) parts.name = name;
        return of(parts);
    }
    /** A name as kept: one line, single spaces, at most {@link #NAME_MAX} characters. */
    static String clean(String name) {
        String clean = name.replaceAll("\\s+", " ").trim();
        return clean.length() > NAME_MAX ? clean.substring(0, NAME_MAX).trim() : clean;
    }
}
