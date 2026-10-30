package io.github.mpdairy.monopaint;

/**
 * The shades: the three the old tools offered (dark, the default, light and lighter) and, since 2026-10-19, mid between
 * dark and light (the owner's fourth grey). Ink was black, grey or light grey, wash dark, light or lighter, watercolor
 * deep, pale or paler (how dark it could get). Shades are kept as the grey swatches' place ({@link ToolSwatches}); the
 * greys here are what a slot converted from an old tool keeps ({@link LegacyBrushes.Old#darkness}), and ink's are the
 * grey swatches' defaults.
 */
final class InkWashShades {
    /** In the order the bar shows them. Saved by name, so never rename one; the order may change. */
    enum Shade {
        DARK("Dark"), MID("Mid"), LIGHT("Light"), LIGHTER("Lighter");
        final String label;
        Shade(String label) { this.label = label; }
        /** The shade saved as {@code name}: dark for none or an unknown name. */
        static Shade saved(String name) {
            for (Shade shade : values()) if (shade.name().equals(name)) return shade;
            return DARK;
        }
    }
    /**
     * Ink's dark grey, grey and light grey shades; the dark one is black. The dark grey (2026-10-19) halves the widest
     * step, black to grey: 0, 60, 120, 175 (steps of 60, 60 and 55, and 80 to white).
     */
    static final int INK_DARK_GRAY = 60, INK_GRAY = 120, INK_LIGHT_GRAY = 175;
    /** Wash's flat greys: about 20% dark, about 15% (no old wash had it), about 10%, and about 4%. */
    static final int WASH_DARK = 205, WASH_MID = 218, WASH_LIGHT = 230, WASH_LIGHTER = 245;
    /** Mid watercolor gets this share as dark as deep watercolor (no old watercolor had it), between deep and pale. */
    static final float WATERCOLOR_MID = .78f;
    /** Pale watercolor gets this share as dark as deep watercolor, edge and all. */
    static final float WATERCOLOR_PALE = .55f;
    /** Paler watercolor gets this share as dark as deep watercolor. */
    static final float WATERCOLOR_PALER = .3f;

    /** The grey ink lays in {@code shade}. */
    static int ink(Shade shade) {
        switch (shade) {
            case MID: return INK_DARK_GRAY;
            case LIGHT: return INK_GRAY;
            case LIGHTER: return INK_LIGHT_GRAY;
            default: return 0;
        }
    }
    /** The flat grey a wash lays in {@code shade}. */
    static int wash(Shade shade) {
        switch (shade) {
            case MID: return WASH_MID;
            case LIGHT: return WASH_LIGHT;
            case LIGHTER: return WASH_LIGHTER;
            default: return WASH_DARK;
        }
    }
    /** How dark watercolor gets in {@code shade}, as a share of how dark it can get. */
    static float watercolor(Shade shade) {
        switch (shade) {
            case MID: return WATERCOLOR_MID;
            case LIGHT: return WATERCOLOR_PALE;
            case LIGHTER: return WATERCOLOR_PALER;
            default: return 1;
        }
    }

    private InkWashShades() {}
}
