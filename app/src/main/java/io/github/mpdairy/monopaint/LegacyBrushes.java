package io.github.mpdairy.monopaint;

import static io.github.mpdairy.monopaint.PartDial.*;

/**
 * Brushes from before the six-part engine, as six-part brushes, so the app runs one engine (plan
 * docs/archive/superpowers/plans/2026-10-15-one-engine.md). An old Wild pen or paint ({@code I}/{@code P} codes, read by
 * {@link BrushCode}) becomes its six parts as the prototype's fromOld (parts.js 651–667): a pen is Ink at Wet 0 on its
 * nib, a paint a round Wash, wet or flat; both dry Slow (re-tapered and settled at lift, as the old engines did) with
 * nothing settling after; pressure and speed move the size, as they did; a paint's lean lays its body down sooner, as
 * the app's old paint did. The four built-ins are fixed six-part brushes tuned on the desktop to draw as close to
 * the old tools as six parts can ({@link #BUILTINS}). A slot that held one keeps what it drew: {@link Old#px} and
 * {@link Old#darkness}. Pure, checked on the desktop.
 */
final class LegacyBrushes {
    /** An old paint's Tilt → size: any amount turns on the lean in the body's width; .1 hardly stretches the dab. */
    static final double PAINT_LEAN = .1;
    /** A flat wash's glaze, which the old Wash's greys reach at this share more darkness (tuned, plan section 3). */
    static final double WASH_DARKER = 1.1;
    /**
     * The built-ins as they were stored ("@INK"), their new names and six-part brushes, tuned against the old tools
     * (plan section 2): Ink a steady Fineliner; Fude a Brush pen on the wide Tilt range (Tilt → size full+), a fine
     * point held upright to nearly five times as wide and flat laid low, as the old Fude drew; Wash a flat Ink wash, tip
     * to full with pressure; Watercolor a wet wash with a hollow edge. Change a code only to draw closer to the old
     * tool: stored built-ins convert to them, and {@link ToolSlots#FIRST} starts on them.
     */
    static final String[][] BUILTINS = {
            {"INK", "Fineliner", "fineliner.M00gx00000000at_000zz0000000000000_0_000_0_8cl0000000000ee0000nz0.b"},
            {"FUDE", "Brush pen", "brush-pen.M10i000000000at_000zz0000000000000_0_000_0_8at0000zz00zz000000nz01.c"},
            {"WASH", "Ink wash", "ink-wash.M70bi0000000090_1000000000000_0_000_200000js_8zz00003m0000000000nz0.d"},
            {"WATERCOLOR", "Watercolour", "watercolour.M60bi000000005e_1zzcl00000000_0_000_29b000js_8zzcl003m0000000000nz0.e"}};

    /** What a stored old brush was, and what it is now. */
    static final class Old {
        /** Its six-part brush, its name kept (a built-in's is its new name). */
        final ModularBrush brush;
        /** The built-in it was ("INK"), or null for a Wild brush. */
        final String builtin;
        /** It drew on the ink layer, in ink's sizes and greys. */
        final boolean pen;
        Old(ModularBrush brush, String builtin, boolean pen) { this.brush = brush; this.builtin = builtin; this.pen = pen; }
        /** What it drew at {@code preset}, px: a pen's ink size (3, 8, 16), a paint's broadest (24, 120, 220). */
        double px(InkWashSizes.Preset preset) { return pen ? InkWashSizes.inkSize(preset) : InkWashSizes.maxSize(preset); }
        /**
         * How dark it drew in {@code shade}, 0–1: a pen its ink grey; the old Wash its flat grey through the glaze; a
         * watercolor its pale share of how dark it can get (1, .55, .3).
         */
        double darkness(InkWashShades.Shade shade) {
            if (pen) return ModularSizes.darkness(shade);
            if ("WASH".equals(builtin)) return StrokeMath.clamp(WASH_DARKER * (1 - InkWashShades.wash(shade) / 255.0) / ModularSizes.GLAZE);
            return InkWashShades.watercolor(shade);
        }
    }

    private LegacyBrushes() { }

    /** A stored line from before the six-part engine ("@INK", or a name and an I/P code), converted; null for anything else. */
    static Old read(String text) {
        if (text == null) return null;
        if (text.startsWith("@")) {
            String name = text.substring(1);
            for (String[] b : BUILTINS) if (b[0].equals(name)) return new Old(builtin(name), name, name.equals("INK") || name.equals("FUDE"));
            return null;
        }
        int tab = text.indexOf('\t');
        if (tab < 0) return null;
        WildBrush wild = BrushCode.decode(text.substring(tab + 1));
        if (wild == null) return null;
        String name = Brush.clean(text.substring(0, tab));
        if (!name.isEmpty()) wild.name = name;
        return new Old(of(wild), null, wild.kind == WildBrush.Kind.INK);
    }
    /** Built-in {@code name}'s six-part brush ("INK": Fineliner), a fresh copy; null for an unknown name. */
    static ModularBrush builtin(String name) {
        for (String[] b : BUILTINS) if (b[0].equals(name)) return named(b);
        return null;
    }
    /** A row of {@link #BUILTINS} or {@link ToolSlots#FIRST} ({key, name, M code}) as a fresh brush under its name. */
    static ModularBrush named(String[] row) {
        ModularBrush brush = ModularCode.decode(row[2]);
        brush.name = row[1];
        return brush;
    }

    /** An old Wild pen or paint as six parts, its name, seed and style kept (Wild is Anything goes). */
    static ModularBrush of(WildBrush o) {
        ModularStyle style = ModularStyle.named(o.style.name);
        ModularBrush b = new ModularBrush(o.name, o.seed, style != null ? style : ModularStyle.named(ModularStyle.ANYTHING_GOES));
        ModularBrush.Part tip, medium, paper = new ModularBrush.Part(PartChoice.of(BrushPart.PAPER, o.texture.ordinal()));
        if (o.kind == WildBrush.Kind.INK) {
            tip = new ModularBrush.Part(o.nib == WildBrush.Nib.ROUND ? PartChoice.ROUND : o.nib == WildBrush.Nib.CHISEL ? PartChoice.CHISEL : PartChoice.FLAT_NIB);
            if (tip.has(ASPECT)) { tip.set(ASPECT, o.aspect); tip.set(ANGLE, o.angle); }
            tip.set(WEIGHT, 1); tip.set(TAPER_IN, o.taperIn); tip.set(TAPER_OUT, o.taperOut); tip.set(DRY, o.dry); tip.set(WOBBLE, o.wobble); tip.set(SOFT, .3);
            medium = new ModularBrush.Part(PartChoice.INK);
            medium.set(WET, 0); medium.set(LINE, 1); medium.set(POOL, o.pool); medium.set(SPLATTER, o.splatter);
            paper.set(TOOTH, o.tooth);
        } else {
            tip = new ModularBrush.Part(PartChoice.ROUND);
            tip.set(WEIGHT, 1); tip.set(DRY, o.streaks); tip.set(SOFT, o.soft);
            medium = new ModularBrush.Part(PartChoice.WASH);
            medium.set(WET, o.wet ? 1 : 0); medium.set(POOL, o.hollow); medium.set(BLEED, o.feather); medium.set(BLOOM, o.bloom);
            medium.set(BACKRUN, o.backrun); medium.set(LINE, 0);
            paper.set(TOOTH, o.gran);
        }
        ModularBrush.Part life = new ModularBrush.Part(PartChoice.DRYING);
        life.set(PartDial.DRYING, drying(o.drying)); life.set(SETTLE, 0); life.set(TAKES, 0);
        ModularBrush.Part hand = new ModularBrush.Part(PartChoice.CUSTOM);
        hand.set(PS, o.pressure); hand.set(SS, Math.abs(o.speed)); hand.set(DIR, o.speedDirection() > 0 ? 1 : 0);
        // The app's old paint laid its body down sooner as the pen leaned (the prototype's port of it didn't).
        if (o.kind == WildBrush.Kind.PAINT) hand.set(TS, PAINT_LEAN);
        b.set(BrushPart.TIP, tip); b.set(BrushPart.MEDIUM, medium); b.set(BrushPart.PAPER, paper);
        b.set(BrushPart.LIFE, life); b.set(BrushPart.HAND, ModularBrush.namedHand(hand));
        return b.normalize();
    }
    /** The six-part Drying time that keeps an old Wild paint's wet seconds: 750 + 4250 v² ms is 750 + 19250 d² at d = v √(4250 / 19250). */
    static double drying(double old) { return old * Math.sqrt(4250 / 19250.0); }
}
