package io.github.mpdairy.monopaint;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * An old Wild brush's settings, as its {@code I}/{@code P} share code carries them, per kind and in the Brush Lab
 * mockup's GENES order: a choice (nib, paint, texture) or a dial over a range. Read only to convert old brushes
 * ({@link LegacyBrushes}). Never reorder a kind's list or a choice's options: share codes are written in this
 * order. Pure, checked on the desktop.
 */
enum BrushSetting {
    NIB("Nib", 0, 0, new String[]{"round", "chisel", "flat"}),
    MODE("Paint", 0, 0, new String[]{"wet", "flat"}),
    TEXTURE("Texture", 0, 0, textureLabels()),
    ASPECT("Nib width", .14, .55, null),
    ANGLE("Nib angle", 20, 70, null),
    TAPER_IN("Taper in", 0, 1, null),
    TAPER_OUT("Taper out", 0, 1, null),
    PRESSURE("Pressure", 0, 1, null),
    SPEED("Speed", -1, 1, null),
    DRY("Dry brush", 0, 1, null),
    TOOTH("Paper tooth", 0, 1, null),
    WOBBLE("Wobble", 0, 1, null),
    POOL("Pooling", 0, 1, null),
    SPLATTER("Splatter", 0, 1, null),
    SOFT("Soft edge", 0, 1, null),
    GRAN("Granulation", 0, 1, null),
    FEATHER("Feathered bleed", 0, 1, null),
    HOLLOW("Hollow edge", 0, 1, null),
    BLOOM("Bloom", 0, 1, null),
    STREAKS("Brush streaks", 0, 1, null),
    BACKRUN("Backruns", 0, 1, null),
    DRYING("Drying time", 0, 1, null);

    /** As the old lab labelled it. */
    final String label;
    /** A dial's range; 0 and 0 for a choice. */
    final double min, max;
    /** A choice's options; null for a dial. */
    final String[] choices;

    BrushSetting(String label, double min, double max, String[] choices) {
        this.label = label; this.min = min; this.max = max; this.choices = choices;
    }

    private static final List<BrushSetting> INK = Collections.unmodifiableList(Arrays.asList(NIB, TEXTURE, ASPECT, ANGLE, TAPER_IN, TAPER_OUT,
            PRESSURE, SPEED, DRY, TOOTH, WOBBLE, POOL, SPLATTER));
    private static final List<BrushSetting> PAINT = Collections.unmodifiableList(Arrays.asList(MODE, TEXTURE, PRESSURE, SOFT, GRAN, FEATHER,
            HOLLOW, BLOOM, STREAKS, BACKRUN, DRYING));

    /** A kind's settings, in order. */
    static List<BrushSetting> of(WildBrush.Kind kind) { return kind == WildBrush.Kind.INK ? INK : PAINT; }

    boolean choice() { return choices != null; }
    /** Only shaped nibs (chisel, flat) have a width and an angle. */
    boolean nibOnly() { return this == ASPECT || this == ANGLE; }
    /** Only wet paint has a hollow edge, blooms and backruns. */
    boolean wetOnly() { return this == HOLLOW || this == BLOOM || this == BACKRUN; }
    /** Whether the setting applies to {@code b}: a round nib has no width or angle, flat paint no wet effects. */
    boolean active(WildBrush b) { return !(nibOnly() && b.nib == WildBrush.Nib.ROUND) && !(wetOnly() && !b.wet); }

    /** The setting on {@code b}: a choice's option index, or a dial's value. */
    double get(WildBrush b) {
        switch (this) {
            case NIB: return b.nib.ordinal();
            case MODE: return b.wet ? 0 : 1;
            case TEXTURE: return b.texture.ordinal();
            case ASPECT: return b.aspect;
            case ANGLE: return b.angle;
            case TAPER_IN: return b.taperIn;
            case TAPER_OUT: return b.taperOut;
            case PRESSURE: return b.pressure;
            case SPEED: return b.speed;
            case DRY: return b.dry;
            case TOOTH: return b.tooth;
            case WOBBLE: return b.wobble;
            case POOL: return b.pool;
            case SPLATTER: return b.splatter;
            case SOFT: return b.soft;
            case GRAN: return b.gran;
            case FEATHER: return b.feather;
            case HOLLOW: return b.hollow;
            case BLOOM: return b.bloom;
            case STREAKS: return b.streaks;
            case BACKRUN: return b.backrun;
            default: return b.drying;
        }
    }
    /** Sets the setting on {@code b}: a choice's option index, or a dial's value. Nothing else changes; see {@link WildBrush#normalize}. */
    void set(WildBrush b, double value) {
        switch (this) {
            case NIB: b.nib = WildBrush.Nib.values()[(int)value]; break;
            case MODE: b.wet = (int)value == 0; break;
            case TEXTURE: b.texture = BrushTexture.values()[(int)value]; break;
            case ASPECT: b.aspect = value; break;
            case ANGLE: b.angle = value; break;
            case TAPER_IN: b.taperIn = value; break;
            case TAPER_OUT: b.taperOut = value; break;
            case PRESSURE: b.pressure = value; break;
            case SPEED: b.speed = value; break;
            case DRY: b.dry = value; break;
            case TOOTH: b.tooth = value; break;
            case WOBBLE: b.wobble = value; break;
            case POOL: b.pool = value; break;
            case SPLATTER: b.splatter = value; break;
            case SOFT: b.soft = value; break;
            case GRAN: b.gran = value; break;
            case FEATHER: b.feather = value; break;
            case HOLLOW: b.hollow = value; break;
            case BLOOM: b.bloom = value; break;
            case STREAKS: b.streaks = value; break;
            case BACKRUN: b.backrun = value; break;
            default: b.drying = value;
        }
    }
    /** The textures an old code may name ({@link BrushTexture#LEGACY}): its codes read as they always did. */
    private static String[] textureLabels() {
        BrushTexture[] textures = BrushTexture.values();
        String[] labels = new String[BrushTexture.LEGACY];
        for (int i = 0; i < labels.length; i++) labels[i] = textures[i].label;
        return labels;
    }
}
