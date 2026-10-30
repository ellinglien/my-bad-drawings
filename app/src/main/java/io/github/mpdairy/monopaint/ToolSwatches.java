package io.github.mpdairy.monopaint;

import java.util.Locale;

/**
 * The four size swatches and four grey swatches, and each tool's own size and grey (the owner, 2026-10-27: "i think we
 * need per tool memory for size and shade"; one size and one grey for every brush from 2026-10-24 to 27). The size
 * slider, the size swatches, the grey strip, the grey bar and the Dropper set the picked tool's ({@link #useSize},
 * {@link #useGrey}); picking another tool brings back its own ({@link #look}), and a brush picked into a slot takes the
 * slot's, unless it brings its own ({@link #LOOK}, 2026-10-28). The eraser has no grey. Kept in the slots' store, read each time. Pure, checked on the desktop.
 * <p>
 * Four swatches since 2026-10-19 (the owner's "1 more size and swatch option"), three before. Each list's three older
 * swatches stay where they were always kept, in their order (so an older build reads them as it did); the fourth, at
 * {@link #NEW_SIZE} and {@link #NEW_GREY}, is kept under a key of its own and, until it is set, sits in the widest step:
 * the size between M and the old L (now XL) on a log scale, the grey halfway between Dark and Light, of what those are
 * now. The size swatches are the Tools page's and Brush Lab's; the grey swatches Grey Shift's and Gradient's set.
 * <p>
 * A tool's px is kept under {@link #PX} (the eraser's under {@link #ERASER_PX}), its darkness under {@link #DARK}; older
 * builds' swatches ({@link ToolSlots#SIZE}, {@link ToolSlots#SHADE}) and greys from before washes reached black
 * ({@link #GREY}) still read ({@link #own}). The one size and grey of 2026-10-24 to 27, where kept, become every
 * brush's own once ({@link #spreadCurrent}).
 */
final class ToolSwatches {
    /** How many size swatches there are, and grey swatches. */
    static final int COUNT = 4;
    /**
     * The swatches at first: 4, 16, 44 and 120 px (44, M's and XL's geometric mean, makes the steps nearly even: ×4, ×2.75,
     * ×2.7); the ink greys' darkness (Dark, Mid, Light, Lighter: Mid halves the widest step, black to grey).
     */
    static final double[] SIZES = {4, 16, 44, 120},
            GREYS = {1, 1 - InkWashShades.INK_DARK_GRAY / 255.0, 1 - InkWashShades.INK_GRAY / 255.0, 1 - InkWashShades.INK_LIGHT_GRAY / 255.0};
    /** Where each list's fourth swatch (2026-10-19) is: the size between M and XL, the grey between Dark and Light. */
    static final int NEW_SIZE = InkWashSizes.Preset.LARGER.ordinal(), NEW_GREY = InkWashShades.Shade.MID.ordinal();
    /** The size modal's stops, px (ui.js 339). */
    static final double[] STOPS = {2, 4, 8, 16, 32, 64, 120, 240, 360};
    /**
     * Each tool's px and darkness under these plus its key; the two lists' three older swatches, as they were always
     * kept; their fourths.
     */
    static final String PX = "inkwash_px_", GREY = "inkwash_grey_", SIZE_SWATCHES = "inkwash_size_swatches", GREY_SWATCHES = "inkwash_grey_swatches",
            SIZE_FOURTH = "inkwash_size_swatch_fourth", GREY_FOURTH = "inkwash_grey_swatch_fourth";
    /** Each tool's darkness picked since washes reached black, under this plus its key; before, {@link #GREY}'s. */
    static final String DARK = "inkwash_dark_";
    /** The one grey and the one size every brush drew at from 2026-10-24 to 27, darkness and px; read once ({@link #spreadCurrent}). */
    static final String CURRENT = "inkwash_current_grey", CURRENT_PX = "inkwash_current_px";
    /**
     * The eraser's key ({@link InkWash#key}); its own size, px (the owner, 2026-10-24: "eraser size stays same size for
     * eraser", and 2026-10-25: "eraser keeps its own size"), set by the slider while the eraser is picked.
     */
    static final String ERASER_KEY = "ERASER", ERASER_PX = "inkwash_eraser_px";
    /** The eraser's finest size, px: twice its tip ({@link InkWashSizes#TIP}). */
    static final double ERASER_LEAST = 2 * InkWashSizes.TIP;

    /** A list of swatches as kept: its older three under {@code key}, its fourth, at {@code at}, under {@code fourth}; each from {@code low} to {@code high}. */
    private static final class Kept {
        final String key, fourth;
        final double[] defaults;
        final int at;
        final double low, high;
        /** Sizes step on a log scale, greys evenly. */
        final boolean log;
        Kept(String key, String fourth, double[] defaults, int at, double low, double high, boolean log) {
            this.key = key; this.fourth = fourth; this.defaults = defaults; this.at = at; this.low = low; this.high = high; this.log = log;
        }
        /** The fourth's value until it is set: between its neighbours, as the slider would set it (a size) or halfway (a grey). */
        double between(double before, double after) { return log ? sizeAt(position(Math.sqrt(before * after))) : (before + after) / 2; }
    }
    private static final Kept SIZE_LIST = new Kept(SIZE_SWATCHES, SIZE_FOURTH, SIZES, NEW_SIZE, ModularSizes.MIN, ModularSizes.MAX, true),
            GREY_LIST = new Kept(GREY_SWATCHES, GREY_FOURTH, GREYS, NEW_GREY, 0, 1, false);

    /** What a tool draws at: its size swatch as a preset (the eraser's size), and px and darkness (a brush's). */
    static final class Look {
        final InkWashSizes.Preset preset;
        final double px, darkness;
        Look(InkWashSizes.Preset preset, double px, double darkness) { this.preset = preset; this.px = px; this.darkness = darkness; }
    }

    private final BrushLibrary.Store store;

    ToolSwatches(BrushLibrary.Store store) { this.store = store; }

    /** Size swatch {@code i}, px. */
    double size(int i) { return list(SIZE_LIST)[i]; }
    /** Grey swatch {@code i}, darkness 0–1. */
    double grey(int i) { return list(GREY_LIST)[i]; }
    /** What the tool kept under {@code key}, holding {@code brush} (null: the eraser), draws at: its own size and grey ({@link #own}). */
    Look look(String key, Brush brush) {
        Look own = own(key, brush);
        return new Look(InkWashSizes.Preset.values()[nearestSize(own.px)], own.px, own.darkness);
    }
    /**
     * Once (per tool again, 2026-10-27): the one size and grey of 2026-10-24 to 27, where kept, become the own of each
     * tool kept under {@code keys}, so every brush draws on as it did; then they are let go (last, so a run cut short repeats).
     */
    void spreadCurrent(Iterable<String> keys) {
        Double px = number(store.get(CURRENT_PX)), darkness = number(store.get(CURRENT));
        for (String key : keys) {
            if (px != null) useSize(key, px);
            if (darkness != null) useGrey(key, darkness);
        }
        store.put(CURRENT_PX, null);
        store.put(CURRENT, null);
    }
    /**
     * What the tool kept under {@code key} draws at by its own size and grey: a brush at its stored px, else its style's
     * size for its size swatch; the eraser at its own px; its stored darkness, else its grey swatch's. A darkness kept
     * before washes reached black (or none, at its swatch's) draws as it did ({@link ModularSizes#sinceBlack}).
     */
    Look own(String key, Brush brush) {
        InkWashSizes.Preset preset = InkWashSizes.Preset.saved(store.get(ToolSlots.SIZE + key));
        double px = diameter(brush, preset), darkness = grey(InkWashShades.Shade.saved(store.get(ToolSlots.SHADE + key)).ordinal());
        if (brush == null) {
            Double own = number(store.get(ERASER_PX));
            return new Look(preset, own != null ? StrokeMath.clamp(own, ERASER_LEAST, ModularSizes.MAX) : px, darkness);
        }
        Double ownPx = number(store.get(PX + key)), own = number(store.get(DARK + key)), before = number(store.get(GREY + key));
        return new Look(preset, ownPx != null ? StrokeMath.clamp(ownPx, ModularSizes.MIN, ModularSizes.MAX) : px,
                own != null ? StrokeMath.clamp(own) : ModularSizes.sinceBlack(brush.parts, before != null ? StrokeMath.clamp(before) : darkness));
    }
    /** The tool kept under {@code key} draws at size swatch {@code i}. */
    void chooseSize(String key, int i) { useSize(key, size(i)); }
    /** Sets size swatch {@code i} to {@code px} (1.5–360), and the tool kept under {@code key} draws at it. */
    void setSize(String key, int i, double px) {
        put(SIZE_LIST, i, px);
        chooseSize(key, i);
    }
    /**
     * The tool kept under {@code key} draws at {@code px} (1.5 to 360), its own, no swatch changed: the side column's size
     * slider (the owner, 2026-10-24: "change the four size options to a quick size slider"), a size swatch. The eraser
     * no finer than {@link #ERASER_LEAST}.
     */
    void useSize(String key, double px) {
        if (ERASER_KEY.equals(key)) store.put(ERASER_PX, write(StrokeMath.clamp(px, ERASER_LEAST, ModularSizes.MAX)));
        else store.put(PX + key, write(StrokeMath.clamp(px, ModularSizes.MIN, ModularSizes.MAX)));
    }
    /** The size swatch the tool kept under {@code key} draws at, or -1 when its size is one of its own (the slider's) or there is none yet. */
    int sizeInUse(String key) {
        Double px = number(store.get(PX + key));
        for (int i = 0; px != null && i < COUNT; i++) if (Math.abs(px - size(i)) < 1e-6) return i;
        return -1;
    }
    /**
     * The Dropper (the owner, 2026-10-24: "add a dropper tool so people can quickly match color"): the tool kept under
     * {@code key} paints at the grey {@code brush} lays {@code tone} at (0 to 255, the page where the pen picked), as the
     * grey strip's tap sets one ({@link #useGrey}); {@code brush} null (the eraser or the smudge tool in use): the grey as
     * the page shows it. Returns that darkness.
     */
    double drop(String key, Brush brush, int tone) {
        double darkness = brush == null ? StrokeMath.clamp(1 - tone / 255.0) : ModularSizes.laying(brush.parts, tone);
        useGrey(key, darkness);
        return darkness;
    }
    /** The tool kept under {@code key} paints at {@code darkness} (0–1), its own (the grey strip's tap, the grey bar's, the Dropper's). */
    void useGrey(String key, double darkness) { store.put(DARK + key, write(StrokeMath.clamp(darkness))); }
    /**
     * A named brush's own size and grey (the owner, 2026-10-28, reviewing their saved brushes: "consider the ideal size
     * and shade for each tool... since we have to set that now"), kept under this plus its share code ({@link
     * ModularCode#encode}: its name's slug and its settings, so a Basic tool of the same settings under another name has
     * none): px, then darkness after a comma, none for a brush without a grey. Apart from the library's lines, so its
     * rewrites keep it. Set by {@link LibraryCleanup}; a slot the brush is put in takes it ({@link #bring}).
     */
    static final String LOOK = "brush_look_";
    /** {@code brush} brings {@code px}, and {@code darkness} (null: none, the slot's grey stays) into any slot it is put in. */
    void setLook(Brush brush, double px, Double darkness) {
        store.put(LOOK + ModularCode.encode(brush.parts), write(px) + (darkness == null ? "" : "," + write(darkness)));
    }
    /**
     * {@code from} was saved over as {@code to} (Brush Lab's Save, {@link BrushLibrary#update}): its own size and grey, if
     * it has them, are {@code to}'s now, unless {@code to} has its own already.
     */
    void moveLook(Brush from, Brush to) {
        String was = LOOK + ModularCode.encode(from.parts), now = LOOK + ModularCode.encode(to.parts), look = store.get(was);
        if (look == null || was.equals(now)) return;
        if (store.get(now) == null) store.put(now, look);
        store.put(was, null);
    }
    /** {@code brush} was put in the tool kept under {@code key}: the tool takes the brush's own size and grey, where it has them ({@link #LOOK}). */
    void bring(String key, Brush brush) {
        String look = store.get(LOOK + ModularCode.encode(brush.parts));
        if (look == null) return;
        String[] parts = look.split(",");
        Double px = number(parts[0]), darkness = parts.length > 1 ? number(parts[1]) : null;
        if (px != null) useSize(key, px);
        if (darkness != null) useGrey(key, darkness);
    }
    /** The size swatch nearest {@code px} on a log scale; the first of two as near. */
    private int nearestSize(double px) {
        int best = 0;
        for (int i = 1; i < COUNT; i++) if (Math.abs(Math.log(size(i) / px)) < Math.abs(Math.log(size(best) / px))) best = i;
        return best;
    }

    /**
     * What {@code brush}'s pictures in the side column draw it at (its tray row, the brush line's preview): its style's
     * Medium size, whatever the current size, at full strength (the grey is not drawn either). A size picked changes
     * the size and grey dot alone, no row or preview drawn again (the owner, 2026-10-24: "why is it redrawing?").
     */
    static Look picture(Brush brush) { return new Look(InkWashSizes.Preset.MEDIUM, diameter(brush, InkWashSizes.Preset.MEDIUM), 1); }
    /** {@code brush}'s size at {@code preset}, page pixels: its style's ({@link ModularSizes#size}); the eraser's (null) typical. */
    static float diameter(Brush brush, InkWashSizes.Preset preset) {
        return brush == null ? InkWashSizes.typical(preset) : (float)ModularSizes.size(brush.parts, preset);
    }
    /** Where {@code px} lies on the size modal's log slider, 0 at {@link ModularSizes#MIN} to 1 at {@link ModularSizes#MAX} (ui.js 340). */
    static double position(double px) {
        return StrokeMath.clamp(Math.log(px / ModularSizes.MIN) / Math.log(ModularSizes.MAX / ModularSizes.MIN));
    }
    /** The px at slider position {@code t}: in half pixels under 10, whole ones above (ui.js 341). */
    static double sizeAt(double t) {
        double v = ModularSizes.MIN * Math.pow(ModularSizes.MAX / ModularSizes.MIN, StrokeMath.clamp(t));
        return v < 10 ? Math.max(ModularSizes.MIN, Math.round(v * 2) / 2.0) : Math.round(v);
    }
    /**
     * The darkness grey {@code tone} (0–255) stands for: black 1, paper 0. The grey bar ({@link GreyBar}, MonoPaint's
     * color bar) sets a grey swatch to one of {@link ShadeStrip#LEVELS} such tones, one for each of the panel's dot
     * densities; a swatch keeps any darkness 0–1 (older ones, a converted brush's), shown at its density.
     */
    static double darkness(int tone) { return 1 - Math.max(0, Math.min(255, tone)) / 255.0; }
    /** The grey darkness {@code d} is in ink, 255 × (1 − d): where it sits on the grey bar. */
    static int tone(double darkness) { return (int) Math.round(255 * (1 - StrokeMath.clamp(darkness))); }
    /**
     * A grey swatch of {@code darkness} in words, as the tool lays it ({@code shown}, 0–255): "Black", "White", "47%
     * grey"; a wash, which glazes, its glaze first ("50% glaze, 49% grey"), or "Black" where what it lays shows solid
     * black on the panel (a black wash's 97%: {@link ModularSizes#WASH_BLACK}).
     */
    static String greyWords(double darkness, int shown) {
        String laid = shown <= 0 ? "Black" : shown >= 255 ? "White" : percent(1 - shown / 255.0) + " grey";
        if (shown == tone(darkness) || shown >= 255) return laid;
        if (DotPattern.whiteCount(shown) == 0) return "Black";
        return (tone(darkness) == 0 ? "Black" : percent(darkness)) + " glaze, " + (shown <= 0 ? "black" : laid);
    }
    /** A share between none and all as a percentage, never 0 % or 100 % (those are white and black). */
    private static String percent(double share) { return Math.max(1, Math.min(99, Math.round(100 * share))) + "%"; }
    /** {@code px} as the size modal writes it: whole pixels, or a half. */
    static String pxText(double px) { return px == Math.rint(px) ? Long.toString(Math.round(px)) : String.format(Locale.ROOT, "%.1f", px); }
    /** {@code px} on the 300 dpi page in millimetres: "1.4 mm" under 10, "12 mm" above (ui.js 342). */
    static String mm(double px) {
        double m = px * 25.4 / 300;
        return (m < 10 ? String.format(Locale.ROOT, "%.1f", m) : Long.toString(Math.round(m))) + " mm";
    }

    /**
     * The four swatches of {@code list} as kept, each in its range: the older three where they always were (the
     * defaults' if there are none or they don't read), the fourth in its place, as set or, until then, between its
     * neighbours ({@link Kept#between}).
     */
    private double[] list(Kept list) {
        double[] older = older(list);
        if (older == null) older = without(list.defaults, list.at);
        double[] values = new double[COUNT];
        for (int i = 0, k = 0; i < COUNT; i++) if (i != list.at) values[i] = older[k++];
        Double fourth = number(store.get(list.fourth));
        values[list.at] = fourth != null ? StrokeMath.clamp(fourth, list.low, list.high)
                : store.get(list.key) == null ? list.defaults[list.at] : list.between(values[list.at - 1], values[list.at + 1]);
        return values;
    }
    /** The three kept under {@code list}'s key, as an older build keeps them, each in its range; null for none or ones that don't read. */
    private double[] older(Kept list) {
        String text = store.get(list.key);
        if (text == null) return null;
        String[] parts = text.split(",", -1);
        if (parts.length != COUNT - 1) return null;
        double[] values = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            Double v = number(parts[i]);
            if (v == null) return null;
            values[i] = StrokeMath.clamp(v, list.low, list.high);
        }
        return values;
    }
    /** {@code values} without the one at {@code at}. */
    private static double[] without(double[] values, int at) {
        double[] rest = new double[values.length - 1];
        for (int i = 0, k = 0; i < values.length; i++) if (i != at) rest[k++] = values[i];
        return rest;
    }
    /** Sets swatch {@code i} of {@code list} to {@code value}, kept in its range: the older three written as they always were, and the fourth. */
    private void put(Kept list, int i, double value) {
        double[] values = list(list);
        values[i] = StrokeMath.clamp(value, list.low, list.high);
        StringBuilder text = new StringBuilder();
        for (double v : without(values, list.at)) text.append(text.length() == 0 ? "" : ",").append(write(v));
        store.put(list.key, text.toString());
        store.put(list.fourth, write(values[list.at]));
    }
    /** A number as kept: whole ones without a point, else Java's own form, always with a '.'. */
    private static String write(double v) { return v == Math.rint(v) && Math.abs(v) < 1e15 ? Long.toString((long)v) : Double.toString(v); }
    /** A finite number, or null for none or one that doesn't read. */
    private static Double number(String text) {
        if (text == null) return null;
        try {
            double v = Double.parseDouble(text.trim());
            return Double.isNaN(v) || Double.isInfinite(v) ? null : v;
        } catch (NumberFormatException e) { return null; }
    }
}
