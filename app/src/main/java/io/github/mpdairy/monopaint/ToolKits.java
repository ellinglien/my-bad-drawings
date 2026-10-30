package io.github.mpdairy.monopaint;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tool kits (docs/archive/superpowers/specs/2026-10-22-tool-kits-design.md; the owner, 2026-10-22: "you have to have a certain
 * set of tools ready .. maybe sumi tools load several tools in your toolbar"): the Basic tools one way of working needs,
 * in the order it uses them, each with its job in a few words, put on the bar's first slots together from the Tools
 * page. Kept in an editable text file, {@link #FILE} (as the brushes' own words are): a kit is "Kit: Name", "Intro: one
 * sentence", then "Basic tool: Job" a brush, and at most one "Eraser Kind: Job" that sets the eraser's kind as it
 * loads. Pure, checked on the desktop ({@code ToolKitsChecks}).
 */
final class ToolKits {
    static final String FILE = "brushes/kits.txt";
    /** The Tools page's words (docs/TEXT-STYLE.md): the library's tab, This Brush's button (the sheet's too), the sheet's other button. */
    static final String TAB = "Kits", LOAD = "Load Kit", CANCEL = "Cancel";
    /** The eraser's line starts with this, then its kind's label ({@link EraseKind#LABELS}). */
    static final String ERASER = "Eraser";

    /** A brush of a kit, or its eraser step: the Basic tool's name (null for the eraser) and its job. */
    static final class Role {
        final String preset, job;
        /** The eraser's kind ({@link EraseKind#LABELS} index) for the eraser's step; -1 for a brush. */
        final int eraser;
        Role(String preset, String job, int eraser) { this.preset = preset; this.job = job; this.eraser = eraser; }
    }

    /** A kit: its name, its intro, its brushes in order, and its eraser step (null: none). */
    static final class Kit {
        final String name, intro;
        final List<Role> brushes;
        final Role eraser;
        Kit(String name, String intro, List<Role> brushes, Role eraser) {
            this.name = name; this.intro = intro; this.brushes = Collections.unmodifiableList(brushes); this.eraser = eraser;
        }
        /** How many slots it fills. */
        int size() { return brushes.size(); }
        /** Its brushes as the Basic tools are ({@link ToolPresets}); null where a name isn't one (the checks find it). */
        Brush[] tools() {
            Brush[] tools = new Brush[brushes.size()];
            for (int i = 0; i < tools.length; i++) { ToolPresets.Preset p = preset(brushes.get(i).preset); tools[i] = p == null ? null : p.brush(); }
            return tools;
        }
        /** Step {@code i}'s words, numbered as the bar says them: "1 Drop ink". The eraser's step comes last. */
        String said(int i) { return (i + 1) + " " + (i < brushes.size() ? brushes.get(i).job : eraser.job); }
        /** The steps' count, the eraser's included. */
        int steps() { return brushes.size() + (eraser == null ? 0 : 1); }
        /** What step {@code i} uses: a Basic tool's name, or "Eraser, Dry Media". */
        String tool(int i) { return i < brushes.size() ? brushes.get(i).preset : ERASER + ", " + EraseKind.LABELS[eraser.eraser]; }
        /** The sheet's title: "Marbling Kit". */
        String title() { return name + " Kit"; }
        /** Whether {@code brush} is one of its brushes, as the Basic tool is. */
        boolean has(Brush brush) {
            for (Brush b : tools()) if (b != null && b.sameAs(brush)) return true;
            return false;
        }
    }

    private ToolKits() {}

    private static List<Kit> all;
    /** Every kit, in the file's order; none when the file is missing. */
    static synchronized List<Kit> all() {
        if (all == null) {
            List<String> lines;
            try { lines = Assets.lines(FILE); } catch (IOException e) { lines = Collections.emptyList(); }
            all = Collections.unmodifiableList(parse(lines));
        }
        return all;
    }
    /** The kits {@code lines} of a kits file hold: a kit with an intro and at least one brush; notes and stray lines left out. */
    static List<Kit> parse(List<String> lines) {
        List<Kit> kits = new ArrayList<>();
        String name = null, intro = null;
        List<Role> brushes = new ArrayList<>();
        Role eraser = null;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            String key = line.substring(0, colon).trim(), value = line.substring(colon + 1).trim();
            if (key.equals("Kit")) {
                if (name != null && intro != null && !brushes.isEmpty()) kits.add(new Kit(name, intro, brushes, eraser));
                name = value; intro = null; brushes = new ArrayList<>(); eraser = null;
            } else if (name == null) {
                continue;
            } else if (key.equals("Intro")) {
                intro = value;
            } else if (key.startsWith(ERASER + " ")) {
                String kind = key.substring(ERASER.length()).trim();
                for (int k = 0; k < EraseKind.LABELS.length; k++) if (EraseKind.LABELS[k].equalsIgnoreCase(kind)) eraser = new Role(null, value, k);
            } else {
                brushes.add(new Role(key, value, -1));
            }
        }
        if (name != null && intro != null && !brushes.isEmpty()) kits.add(new Kit(name, intro, brushes, eraser));
        return kits;
    }
    /** The Basic tool named {@code name} (not a retired one), or null. */
    static ToolPresets.Preset preset(String name) {
        for (ToolPresets.Preset p : ToolPresets.all()) if (p.name.equals(name)) return p;
        return null;
    }
    /** The first kit {@code brush} is in (as the Basic tool is), or null. */
    static Kit of(Brush brush) {
        if (brush == null || ToolPresets.find(brush) == null) return null;
        for (Kit k : all()) if (k.has(brush)) return k;
        return null;
    }

    /**
     * What the bar says beside slot {@code slot} when it is picked: its step in a kit ("3 Comb rings") while that kit's
     * brushes are in the first slots in order ({@code slots}, the bar's brushes), else null (its name, as before).
     */
    static String said(Brush[] slots, int slot) {
        if (slots == null || slot < 0 || slot >= slots.length) return null;
        for (Kit k : all()) {
            Brush[] tools = k.tools();
            if (slot >= tools.length || tools.length > slots.length) continue;
            boolean laid = true;
            for (int i = 0; i < tools.length && laid; i++) laid = tools[i] != null && slots[i] != null && tools[i].sameAs(slots[i]);
            if (laid) return k.said(slot);
        }
        return null;
    }

    // Marbling's order (the Float spec: a bath starts with a Float stroke and prints when anything else draws)

    /** The notes (about three words, docs/TEXT-STYLE.md rule 7): clear water or a comb on paper with nothing floating; another brush printing the rings. */
    static final String NOTHING_FLOATING = "No ink floating", PRINTED = "Rings printed";
    /**
     * The note a stroke of {@code b} gets on a page where paint floats ({@code floating}) or doesn't, or null: a Float
     * brush with no paint of its own (Clear water, Comb) where nothing floats moves nothing; a brush that doesn't float,
     * nor works on the water ({@code onWater}: Clear Water's smudge does), prints the rings first.
     */
    static String floatNote(ModularBrush b, ModularBrush onWater, boolean floating) {
        if (b == null) return floating ? PRINTED : null;
        boolean floats = b.choice(BrushPart.LIFE) == PartChoice.FLOAT;
        if (floats && !floating && ModularBrush.ownPaint(b.part(BrushPart.CARRY)) < .02) return NOTHING_FLOATING;
        if (floating && !floats && (onWater == null || onWater.choice(BrushPart.LIFE) != PartChoice.FLOAT)) return PRINTED;
        return null;
    }
}
