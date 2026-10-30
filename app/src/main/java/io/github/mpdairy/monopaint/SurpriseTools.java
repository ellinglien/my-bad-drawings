package io.github.mpdairy.monopaint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Surprise Me (docs/archive/superpowers/specs/2026-10-19-notebook-options-design.md, "Tools"): a notebook's own slots
 * rolled as a balanced mix, "useful but delightfully unpredictable". Each slot has a role, so the mix is balanced by
 * construction, and every roll and every re-roll of one slot keeps it so:
 * <ul>
 * <li>PEN, always the first slot: one plain fast pen to write with (a simple ink line that dries at once);
 * <li>a range of types: LINE (a fast, simple line tool), WASH (a wash or soft brush), DRY (a dry or textured tool) and two
 * WILD (Anything goes);
 * <li>FANCY: a mischief module (Screen, Tone, Flick, Fling, Mold, Float or gravity), and three more of any kind (one
 * until the ninth and tenth slots, 2026-10-27);
 * <li>so at least two are fast and two simple (the PEN and the LINE), at least one fancy, and none the same as another.
 * </ul>
 * About half come from the owner's library and half are fresh rolls ({@link ModularStyle#roll}): five slots are drawn for
 * the library at random; a role the library has no brush for is rolled fresh instead. Each brush comes with the size and
 * grey it draws at. Pure, checked over many seeds on the desktop ({@code NotebookOptionsChecks}).
 */
final class SurpriseTools {
    private SurpriseTools() {}

    /** A slot's job in the mix. */
    enum Role { PEN, LINE, WASH, DRY, WILD, FANCY, ANY }
    /** The roles of the slots ({@link ToolSlots#COUNT}, checked), before they are mixed up (the pen stays first). */
    static final Role[] PLAN = {Role.PEN, Role.LINE, Role.WASH, Role.DRY, Role.WILD, Role.WILD, Role.FANCY, Role.ANY, Role.ANY, Role.ANY};
    /** How many slots come from the library: half. */
    static final int FROM_LIBRARY = PLAN.length / 2;
    /** Tries at a fresh roll that fits its role before the role's own brush is made. */
    private static final int TRIES = 400;

    /** A slot: its brush, whether it came from the library, its role, and the px and darkness it draws at. */
    static final class Pick {
        final Brush brush; final boolean library; final Role role; final double px, darkness;
        Pick(Brush brush, boolean library, Role role, double px, double darkness) { this.brush = brush; this.library = library; this.role = role; this.px = px; this.darkness = darkness; }
    }

    // What a brush is

    /** The sort of mark a brush makes. */
    enum Kind { LINE, WASH, DRY, WILD }
    /** A brush's sort: Anything goes is WILD; else by what it lays (ink, wash or opaque paint, dry dust) and its tip (bristles, stipple, hatch, rake are dry). */
    static Kind kind(Brush brush) {
        ModularBrush b = brush.parts;
        if (b.style.wild) return Kind.WILD;
        PartChoice tip = b.choice(BrushPart.TIP), medium = b.choice(BrushPart.MEDIUM);
        if (medium == PartChoice.CHARCOAL || medium == PartChoice.GRAPHITE || tip == PartChoice.BRISTLES || tip == PartChoice.STIPPLE
                || tip == PartChoice.HATCH || tip == PartChoice.HAND_HATCH || tip == PartChoice.MULTI_LINE) return Kind.DRY;
        if (medium == PartChoice.WASH || medium == PartChoice.COVER) return Kind.WASH;
        return Kind.LINE;
    }
    /** Whether the brush dries as it is laid, or in the few mm behind the nib (Instant or Quick), with nothing slower in it. */
    static boolean fast(Brush brush) {
        PartChoice life = brush.parts.choice(BrushPart.LIFE);
        return (life == PartChoice.NONE || life == PartChoice.FAST_WET) && !fancy(brush);
    }
    /** Whether the brush is simple: a plain tip, a medium, no smudge, no slow drying and no mischief. */
    static boolean simple(Brush brush) {
        ModularBrush b = brush.parts;
        PartChoice tip = b.choice(BrushPart.TIP), medium = b.choice(BrushPart.MEDIUM);
        boolean plainTip = tip == PartChoice.ROUND || tip == PartChoice.CHISEL || tip == PartChoice.FLAT_NIB || tip == PartChoice.HAKE || tip == PartChoice.FILBERT || tip == PartChoice.SPLIT_NIB;
        boolean plainMedium = medium == PartChoice.INK || medium == PartChoice.WASH || medium == PartChoice.COVER || medium == PartChoice.CHARCOAL || medium == PartChoice.GRAPHITE;
        PartChoice life = b.choice(BrushPart.LIFE);
        return plainTip && plainMedium && b.choice(BrushPart.CARRY) == PartChoice.OFF && (life == PartChoice.NONE || life == PartChoice.FAST_WET) && !fancy(brush);
    }
    /** Whether the brush has a mischief module: Screen (print shading), Tone, Flick, Fling, Mold, Float, Fading, Lemon Juice or a page-tilt (gravity) cell. */
    static boolean fancy(Brush brush) {
        ModularBrush b = brush.parts;
        ModularBrush.Part tip = b.part(BrushPart.TIP), medium = b.part(BrushPart.MEDIUM), life = b.part(BrushPart.LIFE), hand = b.part(BrushPart.HAND);
        if (medium.choice == PartChoice.TONE || life.choice == PartChoice.HALO || life.choice == PartChoice.FLOAT || life.choice == PartChoice.FADE || life.choice == PartChoice.LEMON) return true;
        if (tip.get(PartDial.FLICK) > 0 || medium.get(PartDial.FLING) > 0 || medium.get(PartDial.SCREEN) > 0) return true;
        for (PartDial cell : PartDial.PAGE_CELLS) if (hand.get(cell) > 0) return true;
        return false;
    }
    /** Whether the brush is a plain fast pen to write with: a simple ink line, dry at once, with no wet, pooling, splatter or bleed. */
    static boolean plain(Brush brush) {
        ModularBrush b = brush.parts;
        ModularBrush.Part medium = b.part(BrushPart.MEDIUM);
        PartChoice tip = b.choice(BrushPart.TIP);
        boolean line = tip == PartChoice.ROUND || tip == PartChoice.CHISEL || tip == PartChoice.FLAT_NIB || tip == PartChoice.SPLIT_NIB;
        return line && medium.choice == PartChoice.INK && simple(brush) && fast(brush) && !b.style.wild
                && medium.get(PartDial.WET) == 0 && medium.get(PartDial.SPLATTER) == 0 && medium.get(PartDial.BLEED) == 0 && medium.get(PartDial.BLOOM) == 0;
    }
    /** Whether {@code brush} does {@code role}'s job. */
    static boolean fits(Role role, Brush brush) {
        switch (role) {
            case PEN: return plain(brush);
            case LINE: return kind(brush) == Kind.LINE && fast(brush) && simple(brush);
            case WASH: return kind(brush) == Kind.WASH;
            case DRY: return kind(brush) == Kind.DRY;
            case WILD: return kind(brush) == Kind.WILD;
            case FANCY: return fancy(brush);
            default: return true;
        }
    }

    // Rolling

    /** The slots rolled: the pen first, the rest mixed up. {@code library}: the owner's brushes (may be empty). */
    static List<Pick> roll(List<Brush> library, ToolSwatches swatches, Random dice) {
        boolean[] fromLibrary = new boolean[PLAN.length];
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < PLAN.length; i++) order.add(i);
        Collections.shuffle(order, dice);
        for (int i = 0; i < FROM_LIBRARY; i++) fromLibrary[order.get(i)] = true;
        List<Pick> picks = new ArrayList<>();
        List<Brush> taken = new ArrayList<>();
        for (int i = 0; i < PLAN.length; i++) {
            Pick pick = pick(PLAN[i], fromLibrary[i], library, taken, swatches, dice);
            picks.add(pick); taken.add(pick.brush);
        }
        // Mixed up, but for the pen: the first slot is the one to write with.
        Collections.shuffle(picks.subList(1, picks.size()), dice);
        return picks;
    }
    /** Slot {@code slot} rolled again in its role and from where it came (the library or fresh), the others as they are. */
    static List<Pick> reroll(List<Pick> now, int slot, List<Brush> library, ToolSwatches swatches, Random dice) {
        List<Brush> taken = new ArrayList<>();
        for (int i = 0; i < now.size(); i++) if (i != slot) taken.add(now.get(i).brush);
        Pick old = now.get(slot);
        List<Pick> next = new ArrayList<>(now);
        next.set(slot, pick(old.role, old.library, library, taken, swatches, dice));
        return next;
    }
    /** A brush for {@code role}: from the library if {@code wantLibrary} and it has one that fits and isn't taken, else rolled fresh. */
    private static Pick pick(Role role, boolean wantLibrary, List<Brush> library, List<Brush> taken, ToolSwatches swatches, Random dice) {
        if (wantLibrary) {
            List<Brush> fitting = new ArrayList<>();
            for (Brush b : library) if (fits(role, b) && !has(taken, b)) fitting.add(b);
            if (!fitting.isEmpty()) return made(fitting.get(dice.nextInt(fitting.size())), true, role, swatches);
        }
        return made(fresh(role, taken, dice), false, role, swatches);
    }
    /** A fresh roll that does {@code role}'s job and isn't {@code taken}; after {@link #TRIES} tries, the role's own brush. */
    static Brush fresh(Role role, List<Brush> taken, Random dice) {
        List<ModularStyle> styles = styles(role);
        for (int i = 0; i < TRIES; i++) {
            Brush b = Brush.of(styles.get(dice.nextInt(styles.size())).roll(dice));
            if (fits(role, b) && !has(taken, b)) return b;
        }
        Brush b = Brush.of(styles.get(0).roll(dice));
        // Mischief is rare in a roll: when the tries missed it, a flick is put in (every tip has one).
        if (role == Role.FANCY) b.parts.part(BrushPart.TIP).set(PartDial.FLICK, .5);
        return b;
    }
    private static List<ModularStyle> styles(Role role) {
        List<ModularStyle> styles = new ArrayList<>();
        switch (role) {
            case PEN: styles.add(style("Fineliner")); break;
            case LINE: add(styles, "Fineliner", "Brush pen", "Dip pen", "Marker"); break;
            case WASH: add(styles, "Watercolor", "Ink wash", "Gouache", "Hake"); break;
            case DRY: add(styles, "Pencil", "Charcoal", "Pastels", "Dry brush"); break;
            case WILD: styles.add(style(ModularStyle.ANYTHING_GOES)); break;
            case FANCY: styles.add(style("Brush pen")); for (ModularStyle s : ModularStyle.all()) if (!styles.contains(s)) styles.add(s); break;
            default: styles.addAll(ModularStyle.all());
        }
        return styles;
    }
    private static void add(List<ModularStyle> into, String... names) { for (String n : names) into.add(style(n)); }
    private static ModularStyle style(String name) { return ModularStyle.named(name); }
    private static boolean has(List<Brush> brushes, Brush brush) { for (Brush b : brushes) if (b.sameAs(brush)) return true; return false; }

    /** {@code brush} as a slot's, at the size and grey it starts with: its style's medium size; Dark, but a wash Light and opaque paint Lighter. */
    private static Pick made(Brush brush, boolean library, Role role, ToolSwatches swatches) {
        PartChoice medium = brush.parts.choice(BrushPart.MEDIUM);
        int grey = medium == PartChoice.WASH ? 2 : medium == PartChoice.COVER ? 3 : 0;
        return new Pick(brush, library, role, ToolSwatches.diameter(brush, InkWashSizes.Preset.MEDIUM), swatches.grey(grey));
    }

    /**
     * A notebook's own toolbar as picks, to roll one slot of it again: each in the role it does best (the first the pen when
     * it is plain; a fast simple line a line; a wash, a dry tool, a wildcard; a lone fancy brush the fancy one; any
     * other any), and from the library when {@code library} has it.
     */
    static List<Pick> picks(ToolSlots.Toolbar toolbar, List<Brush> library) {
        List<Pick> picks = new ArrayList<>();
        int fancy = 0;
        for (Brush b : toolbar.brushes) if (fancy(b)) fancy++;
        for (int i = 0; i < ToolSlots.COUNT; i++) {
            Brush b = toolbar.brushes[i];
            Role role = i == 0 && plain(b) ? Role.PEN : fancy == 1 && fancy(b) ? Role.FANCY : fits(Role.LINE, b) ? Role.LINE
                    : kind(b) == Kind.WASH ? Role.WASH : kind(b) == Kind.DRY ? Role.DRY : kind(b) == Kind.WILD ? Role.WILD : Role.ANY;
            picks.add(new Pick(b, has(library, b), role, toolbar.px[i], toolbar.darkness[i]));
        }
        return picks;
    }
    /** The picks as a toolbar for the slots ({@link ToolSlots#load}). */
    static ToolSlots.Toolbar toolbar(List<Pick> picks) {
        ToolSlots.Toolbar toolbar = new ToolSlots.Toolbar();
        for (int i = 0; i < ToolSlots.COUNT; i++) { toolbar.brushes[i] = picks.get(i).brush; toolbar.px[i] = picks.get(i).px; toolbar.darkness[i] = picks.get(i).darkness; }
        return toolbar;
    }

    /** What is wrong with the mix, or null when it is balanced: the pen first; two fast, two simple, one fancy; a line, a wash, a dry and two wildcards; none the same. */
    static String problem(List<Pick> picks) {
        if (picks.size() != ToolSlots.COUNT) return "Every slot";
        if (!plain(picks.get(0).brush)) return "A plain fast pen to write with, first";
        int fast = 0, simple = 0, fancy = 0, line = 0, wash = 0, dry = 0, wild = 0;
        for (int i = 0; i < picks.size(); i++) {
            Brush b = picks.get(i).brush;
            if (fast(b)) fast++;
            if (simple(b)) simple++;
            if (fancy(b)) fancy++;
            switch (kind(b)) { case LINE: line++; break; case WASH: wash++; break; case DRY: dry++; break; default: wild++; }
            for (int j = 0; j < i; j++) if (picks.get(j).brush.sameAs(b)) return "Never two the same";
        }
        if (fast < 2) return "At least two fast";
        if (simple < 2) return "At least two simple";
        if (fancy < 1) return "At least one fancy";
        if (line < 1 || wash < 1 || dry < 1 || wild < 2) return "A range of types";
        return null;
    }
}
