package io.github.mpdairy.monopaint;

/**
 * Sizes, shades and sample sizes for six-part brushes (spec "Sizes and shades"): every size a six-part brush draws
 * at on the page comes from {@link #size}, never under {@link #MIN} or over {@link #MAX} pixels. Pure, checked on the
 * desktop.
 */
final class ModularSizes {
    /** The narrowest and broadest a six-part brush draws, page pixels. */
    static final double MIN = 1.5, MAX = 360;
    /** Anything goes' Medium: the app's own, for ink, charcoal and graphite and for wash and cover. */
    static final double ANYTHING_GOES_INK = 8, ANYTHING_GOES_PAINT = 120;
    /**
     * Small, Larger and Large as shares of Medium; Larger (the fourth size, 2026-10-19) between Medium and Large on a log
     * scale (√2).
     */
    static final double SMALL = .4, LARGER = 1.4, LARGE = 2;
    /**
     * A body glazes at this share of a shade's darkness, as Wild paint does: ink's wet body and opaque paint's, and a
     * wash's until 2026-10-19 (so a wash's black was a 34% grey). A grey kept from then draws a wash at this share of it
     * still ({@link #sinceBlack}).
     */
    static final double GLAZE = .34;
    /**
     * A wash's darkness in one stroke at black where its body is full, over white: the grey bar's black (the owner,
     * 2026-10-19: washes reach black), which the panel shows solid black. A wash lays this share of its grey, every grey:
     * half way along the bar, 48.5%. A black wash's strokes lay 87 to 97% at their median (the body is thinner than full in
     * places), its pooled edges more.
     */
    static final double WASH_BLACK = .97;
    /**
     * A body's strength to this lays that darkness ({@link #laid}): every body laid before washes reached black stays
     * under it (GLAZE at density 1.6, the most a body pools, is .544), so each lays as it did.
     */
    static final double KNEE = .55;
    /** Tone's Medium in every style: a broad fill (the prototype's 24 to 48 px), whatever the style's pens. */
    static final double TONE = 40;
    /**
     * A Stamp's Medium in every style, its print's longer side: mischief-lab-2.html's 44 px stamp (its pad's CSS px, three
     * of the Manta's), whatever the style's pens.
     */
    static final double STAMP = 132;
    /**
     * Crayon's Medium in every style: mischief-lab-2.html's crayon at middling pressure, about 10 px across on its pad
     * (its CSS px, three of the Manta's), whatever the style's pens.
     */
    static final double CRAYON = 30;
    /**
     * A Spiro's Medium in every style, its curve's width along the line: mischief-lab-2.html's 22 px curve (its pad's CSS
     * px), each side, three of the Manta's; a ring's radius is {@link SpiroStamper#RING} times its half.
     */
    static final double SPIRO = 132;
    /** Hatching's Medium in every style ({@link HandHatchStamper}): its strokes about this long, 5 mm on the Manta's panel, whatever the style's pens. */
    static final double HATCHING = 60;

    private ModularSizes() { }

    /**
     * Whether it draws on the ink layer, in ink's shades: ink, charcoal or graphite, Resist (laid as an ink line; its
     * fluid goes on the page's masking fluid, wax's sheen on the ink layer) and Tone (its dots, which the eraser
     * scrapes as it goes). Wash and cover paint.
     */
    static boolean inks(ModularBrush b) {
        PartChoice m = b.choice(BrushPart.MEDIUM);
        return m == PartChoice.INK || m == PartChoice.CHARCOAL || m == PartChoice.GRAPHITE || m == PartChoice.RESIST || m == PartChoice.TONE || m == PartChoice.CRAYON;
    }
    /** Its size at {@code preset}: Medium its style's size for its medium (Anything goes: the app's own; a Stamp's {@link #STAMP}, a Spiro's {@link #SPIRO}), Small .4 of it, Larger 1.4 times, Large twice; 1.5–360 px. */
    static double size(ModularBrush b, InkWashSizes.Preset preset) {
        double m = b.stamps() ? STAMP : b.choice(BrushPart.TIP) == PartChoice.SPIRO ? SPIRO : b.choice(BrushPart.TIP) == PartChoice.HAND_HATCH ? HATCHING : b.choice(BrushPart.MEDIUM) == PartChoice.TONE ? TONE : b.choice(BrushPart.MEDIUM) == PartChoice.CRAYON ? CRAYON : b.style.wild ? (inks(b) ? ANYTHING_GOES_INK : ANYTHING_GOES_PAINT) : b.style.size(b.choice(BrushPart.MEDIUM));
        double share;
        switch (preset) {
            case SMALL: share = SMALL; break;
            case LARGER: share = LARGER; break;
            case LARGE: share = LARGE; break;
            default: share = 1;
        }
        return StrokeMath.clamp(share * m, MIN, MAX);
    }
    /** A shade's darkness 0–1: the ink greys' (Dark 1, Mid 1 − 60/255, Light 1 − 120/255, Lighter 1 − 175/255). */
    static double darkness(InkWashShades.Shade shade) { return 1 - InkWashShades.ink(shade) / 255.0; }
    /** The grey a shade's swatch shows (see {@link #swatch(ModularBrush, double)}). */
    static int swatch(ModularBrush b, InkWashShades.Shade shade) { return swatch(b, darkness(shade)); }
    /** The grey a swatch of {@code darkness} 0–1 shows: ink, charcoal, graphite and cover that grey; a wash its glaze. */
    static int swatch(ModularBrush b, double darkness) {
        return (int)Math.round(255 * (1 - (washes(b) ? WASH_BLACK * darkness : darkness)));
    }
    /**
     * The darkness 0 to 1 whose swatch shows {@code tone} 0 to 255 ({@link #swatch(ModularBrush, double)}'s inverse): the
     * Dropper's, so the brush lays the grey it picked. A wash's darkest is its black's {@link #WASH_BLACK}.
     */
    static double laying(ModularBrush b, int tone) {
        double d = 1 - Math.max(0, Math.min(255, tone)) / 255.0;
        return StrokeMath.clamp(washes(b) ? d / WASH_BLACK : d);
    }
    /** Whether {@code b} is a wash: its body glazes, to {@link #WASH_BLACK} at black. */
    static boolean washes(ModularBrush b) { return b.choice(BrushPart.MEDIUM) == PartChoice.WASH; }

    /**
     * The darkness a body of strength {@code x} (its density times its {@link ResolvedBrush#washK}) lays over white:
     * {@code x} itself up to {@link #KNEE}, as every body always laid; past it easing toward black and never quite there,
     * so a black wash's pooled edges, blooms and grain still read in it rather than all going flat black.
     */
    static double laid(double x) { return x <= KNEE ? x : 1 - (1 - KNEE) * Math.exp(-(x - KNEE) / (1 - KNEE)); }
    /** The strength that lays darkness {@code y} (0 to just under 1): {@link #laid}'s inverse. */
    static double strength(double y) { return y <= KNEE ? y : KNEE - (1 - KNEE) * Math.log((1 - Math.min(y, .999)) / (1 - KNEE)); }
    /**
     * A wash's strength at swatch {@code darkness} 0–1: a full body lays {@link #WASH_BLACK} of it, so its swatch's grey
     * ({@link #swatch}) is what it lays; light washes as ever ({@link #GLAZE} of darkness d is {@code WASH_BLACK} of
     * {@code d × GLAZE / WASH_BLACK}).
     */
    static double washStrength(double darkness) { return strength(WASH_BLACK * StrokeMath.clamp(darkness)); }
    /**
     * The darkness that draws {@code b} as {@code before} drew it before washes reached black: a wash at
     * {@code before × GLAZE / WASH_BLACK} (a slot's old Dark at .35, its glaze still 34%), anything else at
     * {@code before}. For the greys kept from then ({@link ToolSwatches#look}) and the darkness samples show.
     */
    static double sinceBlack(ModularBrush b, double before) { return washes(b) ? StrokeMath.clamp(before) * GLAZE / WASH_BLACK : before; }
    /**
     * The darkness a brush's samples show it at (the Tools page's and the library's cards, part cards): black, a wash
     * at the old black ({@link #sinceBlack}), its 34% glaze, so a wash's card looks as it always has: a wash, not ink.
     */
    static double sampleDarkness(ModularBrush b) { return sinceBlack(b, 1); }
    /** ui.js sampleSize (498–509), role "loop": the mark's size in a tile that reaches {@code ref} ({@link WildSample#reach}). */
    static float sampleSize(ModularBrush b, float ref) { return sampleSize(b, ref, null); }
    /**
     * ui.js sampleSize (498–509): the mark's size in a tile that reaches {@code ref} ({@link WildSample#reach} for a
     * loop, {@link WildSample#waveReach} for a wave) for part {@code role}'s card, or null for
     * the loop; Paper's 1.7 times it, so its grain reads, and a Hand's line 1.8 times, so its pressure ramp does.
     */
    static float sampleSize(ModularBrush b, float ref, BrushPart role) {
        PartChoice m = b.choice(BrushPart.MEDIUM), t = b.choice(BrushPart.TIP);
        double s;
        if (m == PartChoice.INK) s = ref / (13 + 14 * b.part(BrushPart.MEDIUM).get(PartDial.WET));
        else if (m == PartChoice.CHARCOAL) s = ref / 9.0;
        // Crayon's tooth and ragged edge are fixed to the page (3 and 7 px): a ninth of a tile reads as salt, not wax;
        // a fifth is about its Medium on a card, so its clumps and broken edge read (the owner, 2026-10-22: "the
        // preview for crayon is pretty poor"). A scratch's line is .3 of it, broad enough to show its crumbled edge.
        else if (m == PartChoice.CRAYON) s = ref / 5.0;
        else if (m == PartChoice.GRAPHITE) s = ref / 16.0;
        else s = t == PartChoice.HAKE ? ref * .24 : ref * .17;
        if (t == PartChoice.BRISTLES && (m == PartChoice.INK || m == PartChoice.GRAPHITE)) s = ref / 8.0;
        // A Stamp's prints are its nib: big enough to read in a tile.
        if (t == PartChoice.STAMP) s = ref * .3;
        // A Spiro's loops, or its ring's rosette, fill about a third of the tile.
        if (t == PartChoice.SPIRO) s = ref * ((int) b.part(BrushPart.TIP).get(PartDial.SPIRO_MODE) != 0 ? .09 : .3);
        // Hatching's strokes, a few rows of them, fill about a quarter of the tile.
        if (t == PartChoice.HAND_HATCH) s = ref * .25;
        if (role == BrushPart.PAPER) s *= 1.7;
        if (role == BrushPart.HAND && s < ref / 7.0) s *= 1.8;
        return (float)Math.max(2, s);
    }
    /** ui.js renderSample's loop pad: a line keeps twice its size and 6 px from the sides, a body .55 of it and 4; at least 4. */
    static float samplePad(float size, float ref) { return (float)Math.max(4, size < ref / 7.0 ? size * 2.0 + 6 : size * .55 + 4); }
}
