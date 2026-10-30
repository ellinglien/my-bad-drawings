package io.github.mpdairy.monopaint;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Six-part share codes, also how six-part brushes are stored, as the prototype's encodeM and its M reading (parts.js
 * 544–597), byte for byte: {@code name.M<style><tip>_<medium>_<carry>_<paper>_<life>_<hand>.<seed>}; a Stamp tip with
 * a nib adds {@code _<nib>} after the hand, the nib's id in base 36 ({@link StampNib}). Each part is its
 * choice's index as one base-36 digit, then its choice's dials in order: a choice dial one digit, any other two base-36
 * digits over the dial's range (not the choice's narrower one). New dials go on the end of a choice's list; a code
 * that stops early reads them as defaults. Dials the prototype doesn't have ({@link #ADDED}) are written only when
 * one of them is off its default, so a brush that doesn't use them keeps its code, the prototype's. Pure, checked on
 * the desktop.
 */
final class ModularCode {
    /**
     * Dials added after the prototype's, last in their choices' lists: Tilt range; gravity's (Runs, Drips, Pool, Sink,
     * Blend, Dust and the hand's page tilt); Bristles' Ink load; every medium's Erasable and Blend; Stipple's Build-up;
     * Smear's Soften; Screen and its dials (the mischief modules spec), and Tone's; Drag's and Smear's Keeps Paint;
     * every tip's Flick and every Blend medium's Fling (Stage 2); Drag's and Smear's Paint Load and Starting Load (the
     * oil paint spec); the hand's Spiro drives, Grey Shift and Gradient; Wet's Stays Wet (the wet paint spec); the hand's
     * Tilt Shading.
     */
    static final Set<PartDial> ADDED = Collections.unmodifiableSet(EnumSet.of(PartDial.TILT_RANGE, PartDial.RUNS, PartDial.DRIPS,
            PartDial.G_POOL, PartDial.SINK, PartDial.BLEND, PartDial.DUST, PartDial.GS, PartDial.GD, PartDial.GW, PartDial.INK_LOAD, PartDial.ERASABLE, PartDial.M_BLEND, PartDial.BUILD_UP, PartDial.SOFTEN, PartDial.KEEPS,
            PartDial.SCREEN, PartDial.CELL, PartDial.S_ANGLE, PartDial.MIS, PartDial.S_GAIN, PartDial.UNEVEN, PartDial.PATTERN, PartDial.TTONE,
            PartDial.FLICK, PartDial.FLING, PartDial.PLOAD, PartDial.PMIN, PartDial.P_DRIVE, PartDial.S_DRIVE, PartDial.T_DRIVE,
            PartDial.SHIFT, PartDial.SHIFT_ORDER, PartDial.SHIFT_GREYS, PartDial.SHIFT_BAR, PartDial.SHIFT_ANY, PartDial.SHIFT_ANY2,
            PartDial.GRADIENT, PartDial.GRAD_TO, PartDial.GRAD_LEN, PartDial.STAYS_WET, PartDial.SIDE_SHADE));
    /** Steps across a dial's range: the most two base-36 digits hold. */
    static final int STEPS = 1295;
    private static final Pattern CODE = Pattern.compile("([a-z]+(?:-[a-z]+)*)\\.M([0-9a-z])([0-9a-z_]+)\\.([0-9a-z]{1,7})");

    private ModularCode() { }

    static String encode(ModularBrush b) {
        return BrushCode.slug(b.name) + ".M" + Integer.toString(b.style.index, 36) + settings(b);
    }
    /** parts.js brushKey: what makes two brushes draw alike, the parts and the seed without name or style. See {@link ModularBrush#key}. */
    static String settings(ModularBrush b) {
        StringBuilder out = new StringBuilder();
        for (BrushPart p : BrushPart.values()) {
            if (p.ordinal() > 0) out.append('_');
            writePart(out, b.part(p));
        }
        // A Stamp's nib, after the six: only a Stamp with one writes it, so every other code is as before.
        if (b.stamps() && b.nib != 0) out.append('_').append(Long.toString(b.nib & 0xffffffffL, 36));
        return out.append('.').append(Long.toString(b.seed & 0xffffffffL, 36)).toString();
    }

    /** The brush an M code describes, its name the code's slug, normalized; null if it isn't an M code that reads. Case doesn't matter. */
    static ModularBrush decode(String code) {
        if (code == null) return null;
        Matcher m = CODE.matcher(BrushCode.written(code.trim()));
        if (!m.matches()) return null;
        ModularStyle style = ModularStyle.at(Character.digit(m.group(2).charAt(0), 36));
        long seed = Long.parseLong(m.group(4), 36);
        String[] segments = m.group(3).split("_", -1);
        BrushPart[] parts = BrushPart.values();
        if (style == null || seed > 0xffffffffL || segments.length < parts.length) return null;
        ModularBrush b = new ModularBrush(m.group(1), seed, style);
        for (BrushPart p : parts) {
            ModularBrush.Part part = readPart(p, segments[p.ordinal()]);
            if (part == null) return null;
            b.set(p, part);
        }
        // Hand Hatch, cut (the refocus, 2026-10-24): its codes still read, as Hatch, the nearest tip.
        if (b.choice(BrushPart.TIP) == PartChoice.HAND_HATCH) b.set(BrushPart.TIP, asHatch(b.part(BrushPart.TIP)));
        // A Stamp's nib: a seventh segment (one that doesn't read is no nib: the round stamp).
        if (b.stamps() && segments.length > parts.length) {
            String nib = segments[parts.length];
            if (nib.matches("[0-9a-z]{1,7}")) { long id = Long.parseLong(nib, 36); if (id <= 0xffffffffL) b.nib = (int) id; }
        }
        return b.normalize();
    }

    /** A Hand Hatch tip as Hatch: the dials the two share (Spacing, Line Weight, Line Angle, the head's turn, Size, Flick) kept, the rest Hatch's defaults. */
    static ModularBrush.Part asHatch(ModularBrush.Part hand) {
        ModularBrush.Part hatch = new ModularBrush.Part(PartChoice.HATCH);
        for (PartDial d : PartChoice.HATCH.dials) if (hand.has(d)) hatch.set(d, hand.get(d));
        return hatch;
    }

    /** parts.js writePart, up to the last dial that isn't an {@link #ADDED} one at its default. */
    private static void writePart(StringBuilder out, ModularBrush.Part part) {
        out.append(Integer.toString(part.choice.index(), 36));
        List<PartDial> dials = part.choice.dials;
        int end = dials.size();
        while (end > 0 && ADDED.contains(dials.get(end - 1)) && part.get(dials.get(end - 1)) == part.choice.defaultOf(dials.get(end - 1))) end--;
        for (PartDial d : dials.subList(0, end)) {
            double v = part.get(d);
            if (d.choice()) { out.append(Integer.toString((int) v, 36)); continue; }
            int step = (int) Math.round((StrokeMath.clamp(v, d.min, d.max) - d.min) / (d.max - d.min) * STEPS);
            if (step < 36) out.append('0');
            out.append(Integer.toString(step, 36));
        }
    }
    /**
     * parts.js readPart (562–577): the choice, then its dials while the segment lasts (the rest stay at defaults;
     * characters left over are ignored); null for an empty segment, a choice past the list or an option past a
     * choice dial's.
     */
    private static ModularBrush.Part readPart(BrushPart p, String segment) {
        if (segment.isEmpty()) return null;
        PartChoice choice = PartChoice.of(p, Character.digit(segment.charAt(0), 36));
        if (choice == null) return null;
        ModularBrush.Part part = new ModularBrush.Part(choice);
        int at = 1;
        for (PartDial d : choice.dials) {
            if (d.choice()) {
                if (at >= segment.length()) break;
                int option = Character.digit(segment.charAt(at++), 36);
                if (option < 0 || option >= d.choices.length) return null;
                part.set(d, option);
                continue;
            }
            if (at + 2 > segment.length()) break;
            int step = Integer.parseInt(segment.substring(at, at + 2), 36);
            at += 2;
            if (step < 0 || step > STEPS) return null;
            part.set(d, d.min + step / (double) STEPS * (d.max - d.min));
        }
        return part;
    }
}
