package io.github.mpdairy.monopaint;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public final class ToolSlotsChecks {
    static Map<String, String> prefs = new HashMap<>();
    static BrushLibrary.Store store() {
        return new BrushLibrary.Store() {
            public String get(String key) { return prefs.get(key); }
            public void put(String key, String value) { if (value == null) prefs.remove(key); else prefs.put(key, value); }
        };
    }
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        start(); reordered(); grown(); tenFromEight(); anySlot(); rollAndSave(); undo(); same(); converted(); interrupted(); unreadable(); smudge(); stamp(); perTool(); currentMigration();
        System.out.println("PASS: ten tool slots start as the owner's toolbar (2026-10-28), Another pencil first, each at its own size and grey from FIRST, the eraser at 31 px, the old first brushes still Basic tools, the library holding them; the owner's tablet holding those ten in the old order takes FIRST's once, sizes, greys, wet ink, the selection and the finger moving with their brushes, any other toolbar left alone; a toolbar kept with six keeps them line for line, at their own sizes, and gains the rest, once; one kept with eight (the app's, the one waiting while a notebook's is open, a notebook's own) keeps its eight in place and gains Cheap ballpoint and Permanent marker; any slot holds any brush; the dice rolls a six-part brush in the slot brush's style; Undo takes back rolls and picks, latest first, until the page changes; slots holding built-ins or old Wild brushes become six-part brushes once, each at its old size and grey, and a conversion cut short loses nothing; putting back the brush a slot holds changes nothing; the smudge tool starts as the Stump, its chooser offers the Basic tools that smudge then saved smudge brushes and nothing that lays paint, and what it takes stays; the stamp tool starts as a Stamp, a cut nib stays across a restart, Undo takes it back, a frozen toolbar doesn't hold it and the slots are untouched; "
                + "each tool's own size and grey (2026-10-27), another's change leaving it, kept through picks, rolls, Undo, a kit, Reset to Default and a restart, a notebook's own toolbar bringing its own and leaving it the app's; "
                + "the first launch since the one size and grey (2026-10-24 to 27) gives them to every slot and the stamp tool, once, the eraser and the smudge tool keeping theirs");
    }
    /** The first launch's toolbar before the owner's (2026-10-28), every slot at 44 px and grey .216: older checks' fixture. */
    static final String[][] OLD_FIRST = {
            {"INK", "Fineliner", "fineliner.M00gx00000000at_000zz0000000000000_0_000_0_8cl0000000000ee0000nz0.b"},
            {"HALFTONE", "Halftone brush", "halftone-brush.Me0i6ati00000ss_0p6000000000000000zz01b2i00090_0_000_0_3p6ll00000000000000nz0.2eh"},
            {"DITHER", "Dither pencil", "dither-pencil.Me0ll00000000at_4zz00zz03_0_1ee_0_3p6ll00000000000000nz0.2eq"},
            {"WAX", "Wax crayon", "wax-crayon.Ma0kp00000000at_7ll0_0_1i0_0_0000000000000000000nz0.2f9"},
            {"SKETCH", "Sketch pencil", "sketch-pencil.M40i000000000at_4at2c_0_di0_1jz_85ezz00000000000000nz000000000530000005000c099zz.inw"},
            {"SLOWWASH", "Slow ink wash", "slow-ink-wash.M70i000nt0000q5_0ts000000cs0000000_0_000_7ss_8qi0000000000000000nz0.1s13pn4"},
            {"OILS", "Flat oils 2", "flat-oils.M925aeei0000000004h_2z3do_2llp6zz_000_7ne_8fh0000k30000000000nz0.2fv"},
            {"BLOOMS", "Weeping Wash of Blooms 2", "weeping-wash-of-blooms.M60i000000000kh_1muek1z9wcf00zz2_0_100_1jz_8sj0000000000000000nz0.1ojoqj5"},
            {"BALLPOINT", "Cheap ballpoint", "cheap-ballpoint.M00i00000002g4l_000zzi00000000003l_0_000_1jz_8h77a00000000000000nz0.ir1"},
            {"MARKER", "Permanent marker", "permanent-marker.M30i0231u10003i_1hx1l1k420000_0_d1g_2clcl0js_81x0000000000000000dk0.ir1"}};
    static Brush old(int slot) { return Brush.of(LegacyBrushes.named(OLD_FIRST[slot])); }
    /** A first launch on {@code store} as it was before 2026-10-28: {@link #OLD_FIRST} in the slots and the library, the eraser at 24 px. */
    static ToolSlots old(BrushLibrary.Store store, Random dice) {
        java.util.List<Brush> firsts = new java.util.ArrayList<>();
        for (int i = 0; i < ToolSlots.COUNT; i++) firsts.add(old(i));
        new BrushLibrary(store).start(firsts);
        // The owner's cleanup renames and removes some of these: LibraryCleanupChecks'.
        store.put(LibraryCleanup.DONE, "1");
        ToolSlots slots = new ToolSlots(store, dice);
        ToolSlots.Toolbar t = new ToolSlots.Toolbar();
        for (int i = 0; i < ToolSlots.COUNT; i++) { t.brushes[i] = old(i); t.px[i] = 44; t.darkness[i] = .216; }
        slots.load(t);
        slots.swatches.useSize(ToolSwatches.ERASER_KEY, 24);
        return slots;
    }
    static Brush style(String name, long seed) { return Brush.of(ModularStyle.named(name).roll(new Random(seed))); }
    static boolean near(double a, double b) { return Math.abs(a - b) < 1e-9; }

    /** The owner's toolbar as the first launch's (2026-10-28): names in order, each slot's own size, px, and grey. */
    static final String[] NAMES = {"Another pencil", "Steady line", "Verve felt", "Fude fountain", "Ink wash", "Watercolour washy", "Rob boss", "Filled shape", "Ben-Day brush", "High lighter"};
    static final double[] PX = {4.5, 3, 4, 2.5, 130, 169, 96, 2.5, 12, 63}, GREYS = {1, 1, 1, 1, .176, .082, 1, 1, 1, .075};
    static void start() {
        prefs = new HashMap<>();
        ToolSlots slots = new ToolSlots(store(), new Random(1));
        check(ToolSlots.COUNT == 10 && ToolSlots.FIRST.length == 10, "Ten slots, a first brush each");
        // The first launch's defaults before 2026-10-28 stay Basic tools.
        for (String old : new String[]{"Halftone brush", "Dither pencil", "Wax crayon", "Sketch pencil", "Slow ink wash", "Cheap ballpoint", "Permanent marker"})
            check(ToolKits.preset(old) != null, old + " is still a Basic tool");
        ToolSwatches s = slots.swatches;
        for (int i = 0; i < ToolSlots.COUNT; i++) {
            check(slots.brush(i).name().equals(NAMES[i]) && ModularCode.encode(slots.brush(i).parts).equals(ToolSlots.FIRST[i][2]), "A first launch's slot " + i + " holds " + NAMES[i] + " as its code says");
            check(ToolSlots.firstPx(i) == PX[i] && ToolSlots.firstGrey(i) == GREYS[i], "FIRST's slot " + i + " at " + PX[i] + " px and grey " + GREYS[i]);
            check(s.look(ToolSlots.key(i), slots.brush(i)).px == PX[i] && s.look(ToolSlots.key(i), slots.brush(i)).darkness == GREYS[i], "Slot " + i + " at its own " + PX[i] + " px and grey " + GREYS[i]);
        }
        check(s.look(ToolSwatches.ERASER_KEY, null).px == 31 && s.sizeInUse(ToolSlots.key(7)) == -1 && s.sizeInUse(ToolSlots.key(0)) == -1, "The eraser at its own 31 px; the slots' sizes their own");
        check("44".equals(prefs.get(ToolSwatches.PX + ToolSlots.STAMP_KEY)) && "0.216".equals(prefs.get(ToolSwatches.DARK + ToolSlots.STAMP_KEY))
                && slots.stamp().parts.choice(BrushPart.TIP) == PartChoice.STAMP && slots.stamp().parts.choice(BrushPart.MEDIUM) == LegacyBrushes.builtin("INK").choice(BrushPart.MEDIUM),
                "The stamp tool as before: a Fineliner stamp at 44 px and grey .216");
        check(!prefs.containsKey(ToolSwatches.CURRENT) && !prefs.containsKey(ToolSwatches.CURRENT_PX), "and no one size or grey");
        check(slots.library.entries().size() == ToolSlots.COUNT && slots.library.entries().get(0).brush.sameAs(slots.brush(0)), "The library holds the ten, Another pencil first");
        check(prefs.containsKey("tool_slots") && new ToolSlots(store(), new Random(2)).brush(2).sameAs(slots.brush(2)), "The slots survive a restart");
        check(prefs.containsKey(ToolSlots.REORDERED), "A first launch needs no reorder");
    }
    /**
     * The owner's tablet (2026-10-28): its toolbar holds FIRST's ten in the old order, the names as typed there. Once,
     * they take FIRST's order, each with its own size, grey and wet ink, the selection and the finger on the same brush,
     * no Undo step; any other toolbar is left alone, and marked done.
     */
    static void reordered() {
        int[] old = {1, 3, 0, 6, 4, 2, 5, 7, 8, 9}; // the FIRST row each old slot held, as the tablet had them: Steady line, Fude fountain, another pencil, rob boss, ...
        prefs = new HashMap<>();
        new ToolSlots(store(), new Random(1));
        String[] lines = new String[ToolSlots.COUNT];
        for (int i = 0; i < ToolSlots.COUNT; i++) {
            int j = old[i];
            lines[i] = ToolSlots.FIRST[j][1].toLowerCase() + "\t" + ToolSlots.FIRST[j][2];
            prefs.put(ToolSwatches.PX + ToolSlots.key(i), Double.toString(100 + j)); prefs.put(ToolSwatches.DARK + ToolSlots.key(i), Double.toString(j / 10.0));
        }
        prefs.put(HeldPaint.WET_INK + ToolSlots.key(3), "0.5");
        prefs.put("tool_slots", String.join("\n", lines)); prefs.remove(ToolSlots.REORDERED);
        prefs.put(ToolSlots.SELECTED, ToolSlots.key(0)); prefs.put(FingerTool.KEY, FingerTool.Choice.SLOT_4.key); // Steady line; rob boss
        ToolSlots slots = new ToolSlots(store(), new Random(1));
        for (int j = 0; j < ToolSlots.COUNT; j++) {
            check(slots.brush(j).parts.key().equals(ToolSlots.first(j).parts.key()) && slots.brush(j).name().equals(NAMES[j].toLowerCase()), "Slot " + j + " holds " + NAMES[j] + ", its name as kept");
            ToolSwatches.Look look = slots.swatches.look(ToolSlots.key(j), slots.brush(j));
            check(look.px == 100 + j && look.darkness == j / 10.0, NAMES[j] + " keeps its own size and grey");
        }
        check("0.5".equals(prefs.get(HeldPaint.WET_INK + ToolSlots.key(6))) && !prefs.containsKey(HeldPaint.WET_INK + ToolSlots.key(3)), "Rob boss keeps its wet ink");
        check(ToolSlots.key(1).equals(prefs.get(ToolSlots.SELECTED)) && FingerTool.Choice.SLOT_7.key.equals(prefs.get(FingerTool.KEY)), "The selection and the finger stay on their brushes");
        check(!slots.canUndo() && "1".equals(prefs.get(ToolSlots.REORDERED)), "No Undo step; done");
        String done = prefs.toString();
        new ToolSlots(store(), new Random(2));
        check(prefs.toString().equals(done), "Once");
        // Any other toolbar: nine of the ten and another brush.
        lines[4] = style("Pencil", 9).write();
        prefs.put("tool_slots", String.join("\n", lines)); prefs.remove(ToolSlots.REORDERED); prefs.put(ToolSlots.SELECTED, ToolSlots.key(0));
        String other = prefs.get("tool_slots");
        new ToolSlots(store(), new Random(1));
        check(prefs.get("tool_slots").equals(other) && ToolSlots.key(0).equals(prefs.get(ToolSlots.SELECTED)) && "1".equals(prefs.get(ToolSlots.REORDERED)), "Another toolbar is left alone, and marked done");
    }
    /**
     * A toolbar kept with six slots (before 2026-10-19): the six stay exactly as they were, line for line; the new
     * slots take the first launch's; each slot keeps its own size; the library and the selection are untouched; once.
     */
    static void grown() {
        prefs = new HashMap<>();
        ToolSlots made = new ToolSlots(store(), new Random(1));
        made.use(0, style("Watercolor", 11)); made.use(5, style("Pencil", 12));
        String[] eight = prefs.get("tool_slots").split("\n", -1);
        String six = String.join("\n", java.util.Arrays.copyOf(eight, 6));
        prefs.put("tool_slots", six);
        for (int i = 6; i < 8; i++) for (String k : new String[]{"inkwash_size_slot", "inkwash_shade_slot", "inkwash_px_slot", "inkwash_grey_slot"}) prefs.remove(k + i);
        prefs.remove(ToolSwatches.CURRENT_PX); prefs.put("inkwash_px_slot5", "33"); prefs.put("inkwash_tool", "slot5");
        String library = prefs.get("brush_library");
        ToolSlots slots = new ToolSlots(store(), new Random(1));
        String[] now = prefs.get("tool_slots").split("\n", -1);
        check(now.length == ToolSlots.COUNT && String.join("\n", java.util.Arrays.copyOf(now, 6)).equals(six), "The six kept slots line for line");
        for (int i = 6; i < ToolSlots.COUNT; i++) check(slots.brush(i).sameAs(ToolSlots.first(i)), "The new slots as a first launch has them: " + i);
        check(slots.swatches.look("slot0", slots.brush(0)).px == ToolSlots.firstPx(0) && slots.swatches.look("slot5", slots.brush(5)).px == 33
                && slots.swatches.look("slot6", slots.brush(6)).px == ToolSwatches.diameter(slots.brush(6), InkWashSizes.Preset.MEDIUM)
                && "slot5".equals(prefs.get("inkwash_tool")) && prefs.get("brush_library").equals(library),
                "each slot at its own size, the new ones at their style's; the selection and the library as they were");
        String before = prefs.toString();
        new ToolSlots(store(), new Random(2));
        check(prefs.toString().equals(before), "Grown once: a second launch changes nothing");
    }
    /**
     * Saved state from the eight-slot builds (before 2026-10-27) loads into ten, the eight kept in place: the slots' lines,
     * their sizes, greys and the selection; the app's toolbar waiting while a notebook's is open; a notebook's own toolbar
     * (its lines in the notebook's options). The ninth and tenth take Cheap ballpoint and Permanent marker.
     */
    static void tenFromEight() {
        prefs = new HashMap<>();
        ToolSlots made = new ToolSlots(store(), new Random(1));
        made.use(0, style("Watercolor", 21)); made.use(7, style("Pencil", 22));
        made.swatches.useSize(ToolSlots.key(3), 17); made.swatches.useGrey(ToolSlots.key(7), .6);
        String[] ten = prefs.get("tool_slots").split("\n", -1);
        String eight = String.join("\n", java.util.Arrays.copyOf(ten, 8));
        prefs.put("tool_slots", eight);
        for (int i = 8; i < 10; i++) { prefs.remove(ToolSwatches.PX + "slot" + i); prefs.remove(ToolSwatches.DARK + "slot" + i); }
        prefs.put("inkwash_tool", "slot7");
        ToolSlots slots = new ToolSlots(store(), new Random(1));
        String[] now = prefs.get("tool_slots").split("\n", -1);
        check(now.length == 10 && String.join("\n", java.util.Arrays.copyOf(now, 8)).equals(eight), "The eight kept slots line for line");
        check(slots.brush(8).sameAs(ToolSlots.first(8)) && slots.brush(9).sameAs(ToolSlots.first(9)), "The two new slots as a first launch has them");
        check(slots.swatches.look("slot3", slots.brush(3)).px == 17 && slots.swatches.look("slot7", slots.brush(7)).darkness == .6 && "slot7".equals(prefs.get("inkwash_tool")),
                "The eight keep their sizes, greys and the selection");
        // A notebook's own toolbar kept with eight lines, in its options and as the toolbar it loads.
        java.util.List<String> eightLines = slots.toolbar().lines().subList(0, 8);
        NotebookOptions options = NotebookOptions.DEFAULT.withTools(NotebookOptions.Tools.LIBRARY, eightLines);
        check(options.ownsTools() && options.toolbar.equals(eightLines), "A notebook's eight-line toolbar is kept as it was");
        StringBuilder record = new StringBuilder(NotebookOptions.Tools.LIBRARY.key);
        for (String line : eightLines) record.append('\n').append(line);
        check(NotebookOptions.DEFAULT.readTools(record.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)).toolbar.equals(eightLines), "and read back from its record");
        ToolSlots.Toolbar own = ToolSlots.of(options);
        check(own != null && own.lines().subList(0, 8).equals(eightLines) && own.brushes[8].sameAs(ToolSlots.first(8)) && own.brushes[9].sameAs(ToolSlots.first(9))
                && own.px[9] == ToolSlots.firstPx(9) && own.darkness[9] == ToolSlots.firstGrey(9), "It loads into ten: its eight in place, the two new as a first launch has them");
        check(ToolSlots.Toolbar.parse(eightLines.subList(0, 7)) == null && ToolSlots.Toolbar.parse(slots.toolbar().lines()) != null, "Seven lines don't read; ten do");
        // The app's toolbar waiting while a notebook's is open, kept with eight lines by the build before.
        prefs.put(ToolSlots.APPWIDE, String.join("\n", slots.toolbar().lines().subList(0, 8)));
        check(slots.appWide().lines().subList(0, 8).equals(slots.toolbar().lines().subList(0, 8)), "The app's toolbar waiting with eight comes back with its eight");
        prefs.remove(ToolSlots.APPWIDE);
    }
    static void anySlot() {
        prefs = new HashMap<>();
        ToolSlots slots = new ToolSlots(store(), new Random(1));
        Brush paint = style("Watercolor", 1);
        slots.use(0, paint);
        slots.use(4, ToolSlots.first(2));
        ToolSlots again = new ToolSlots(store(), new Random(1));
        check(again.brush(0).sameAs(paint) && !again.brush(0).inks() && again.brush(4).name().equals(ToolSlots.FIRST[2][1]), "Any brush goes in any slot, and stays");
    }
    static void rollAndSave() {
        prefs = new HashMap<>();
        ToolSlots slots = old(store(), new Random(2));
        Brush rolled = slots.roll(2);
        check(rolled.parts.style.name.equals("Print") && slots.brush(2).sameAs(rolled), "The dice puts a new six-part brush in the slot's style");
        check(slots.roll(2).parts.style.name.equals("Print"), "and keeps rolling in it");
        check(!slots.isSaved(2) && slots.save(2) == BrushLibrary.Saved.ADDED && slots.isSaved(2) && slots.save(2) == BrushLibrary.Saved.ALREADY, "Save adds the slot's brush once");
        check(slots.library.recent(4).get(0).brush.sameAs(slots.brush(2)), "and makes it the most recent");
        check(slots.isSaved(4) && slots.save(4) == BrushLibrary.Saved.ALREADY, "A first brush is in the library already");
        slots.use(1, old(3));
        check(slots.brush(1).name().equals("Wax crayon") && slots.library.recent(4).get(0).brush.name().equals("Wax crayon") && slots.library.recent(4).size() == 4,
                "Putting a library brush in a slot makes it the most recent of four");
    }
    static void undo() {
        prefs = new HashMap<>();
        ToolSlots slots = old(store(), new Random(3));
        check(!slots.canUndo() && slots.undo() == -1, "Nothing to take back at first: Undo is the page's");
        Brush first = slots.brush(2), firstPaint = slots.brush(5);
        Brush one = slots.roll(2);
        slots.roll(2);
        check(slots.canUndo() && slots.undo() == 2 && slots.brush(2).sameAs(one), "Undo after a roll brings the brush before back");
        check(slots.undo() == 2 && slots.brush(2).sameAs(first) && slots.undo() == -1, "Each Undo takes back one more, then none are left");
        Brush two = slots.roll(2);
        slots.roll(5);
        check(slots.undo() == 5 && slots.brush(5).sameAs(firstPaint) && slots.brush(2).sameAs(two), "Undo takes back the latest change, whichever slot it was in");
        Brush fude = old(1);
        slots.use(0, fude);
        check(slots.undo() == 0 && slots.brush(0).name().equals("Fineliner"), "Undo takes back a pick too");
        slots.use(0, fude); slots.pageChanged(); slots.use(0, fude);
        check(!slots.canUndo() && slots.undo() == -1 && slots.brush(0).sameAs(fude), "Once the page changes, Undo is the page's; picking the brush in use changes nothing");
        slots.roll(2);
        check(new ToolSlots(store(), new Random(4)).undo() == -1, "What Undo can take back is not kept between launches");
        for (int i = 0; i < ToolSlots.UNDOS + 5; i++) slots.roll(2);
        int taken = 0;
        while (slots.undo() != -1) taken++;
        check(taken == ToolSlots.UNDOS, "Undo takes back the latest " + ToolSlots.UNDOS + " at most: " + taken);
    }
    /** The brush a slot already holds, put back (Back from an unchanged lab): no Undo step, no "recently used" bump. */
    static void same() {
        prefs = new HashMap<>();
        ToolSlots slots = old(store(), new Random(7));
        slots.roll(2);
        slots.save(2);
        slots.use(1, old(3));
        slots.pageChanged();
        String before = prefs.toString();
        check(!slots.use(2, Brush.read(slots.brush(2).write())) && !slots.use(1, old(3)), "Putting back the brush a slot holds is no change");
        check(!slots.canUndo() && prefs.toString().equals(before) && slots.library.recent(4).get(0).brush.name().equals("Wax crayon"), "nothing to undo, nothing stamped");
        check(slots.use(1, old(1)) && slots.canUndo(), "Another brush is");
    }

    /** A Stage-1/2 install: the bar as it was (built-ins and Wild brushes), each slot's size and shade, swatches set. */
    static void stage2() {
        prefs = new HashMap<>();
        prefs.put("tool_slots", "@INK\n@FUDE\nwobbly goose\tdapper-fig.I610gavij3f7q16o0000006m00.e1\n@WASH\n@WATERCOLOR\n"
                + "brave-onion\tbrave-onion.M24p6okati000ee00_0atzz9000000020000_0_277_1e0_6ee00ee0000we000000nz0.1u5hvr4");
        prefs.put("inkwash_size_slot0", "LARGE"); prefs.put("inkwash_shade_slot0", "LIGHT");
        prefs.put("inkwash_size_slot1", "SMALL");
        prefs.put("inkwash_size_slot3", "MEDIUM"); prefs.put("inkwash_shade_slot3", "DARK");
        prefs.put("inkwash_size_slot4", "LARGE"); prefs.put("inkwash_shade_slot4", "LIGHTER");
        // Picking a swatch stored a px for old brushes too, which they never drew at.
        prefs.put("inkwash_px_slot0", "120"); prefs.put("inkwash_grey_slot0", "0.53");
        prefs.put("inkwash_size_slot5", "SMALL"); prefs.put("inkwash_px_slot5", "33"); prefs.put("inkwash_grey_slot5", "0.4");
        prefs.put("inkwash_size_ERASER", "LARGE"); prefs.put("inkwash_tool", "slot3");
        prefs.put("brush_library", "1\t0\t@INK\n2\t0\t@FUDE\n3\t0\t@WASH\n4\t0\t@WATERCOLOR");
        // The owner's cleanup would rename these built-ins (Classic fineliner): LibraryCleanupChecks'.
        prefs.put(LibraryCleanup.DONE, "1");
    }
    static void converted() {
        stage2();
        ToolSlots slots = new ToolSlots(store(), new Random(4));
        String text = prefs.get("tool_slots");
        check(!text.contains("@") && !text.contains(".I"), "The slots are stored as M codes: " + text);
        check(slots.brush(0).name().equals("Fineliner") && slots.brush(1).name().equals("Brush pen") && slots.brush(3).name().equals("Ink wash")
                && slots.brush(4).name().equals("Watercolour") && slots.brush(2).name().equals("wobbly goose") && slots.brush(2).parts.style.wild, "Built-ins become their six-part brushes; a Wild brush keeps its name");
        check(slots.brush(5).name().equals("brave-onion") && prefs.get("inkwash_px_slot5").equals("33"), "A six-part slot is left alone");
        // Each slot at its own size and grey (2026-10-27): a converted slot's old swatches, read with its converted brush.
        ToolSwatches s = slots.swatches;
        ToolSwatches.Look wash = s.look("slot3", slots.brush(3));
        check(wash.px == ModularSizes.size(slots.brush(3).parts, InkWashSizes.Preset.MEDIUM) && near(wash.darkness, ModularSizes.sinceBlack(slots.brush(3).parts, 1)),
                "The Ink wash at its old M and Dark");
        check(s.look("slot0", slots.brush(0)).px == 120 && s.look("slot5", slots.brush(5)).px == 33 && s.look("slot4", slots.brush(4)).px == ModularSizes.size(slots.brush(4).parts, InkWashSizes.Preset.LARGE),
                "Each slot at its own old size");
        check(!prefs.containsKey("inkwash_px_slot3") && !prefs.containsKey("inkwash_grey_slot3") && "120".equals(prefs.get("inkwash_px_slot0")), "Nothing written for a converted slot; the old ones left");
        check("LARGE".equals(prefs.get("inkwash_size_ERASER")) && "slot3".equals(prefs.get("inkwash_tool")), "The eraser's old size and the selection stay");
        check(slots.library.entries().size() == 4 && !prefs.get("brush_library").contains("@") && slots.library.entries().get(3).brush.name().equals("Fineliner"),
                "The library's built-ins become six-part brushes too, nothing added");
        String before = prefs.toString();
        new ToolSlots(store(), new Random(5));
        check(prefs.toString().equals(before), "Converted once: a second launch changes nothing");
    }
    /** A conversion that stops after any write (the tablet dies) and runs again ends as one that didn't stop. */
    static void interrupted() {
        stage2();
        new ToolSlots(store(), new Random(4));
        Map<String, String> whole = prefs;
        for (int writes = 0; ; writes++) {
            stage2();
            int[] left = {writes};
            BrushLibrary.Store dies = new BrushLibrary.Store() {
                public String get(String key) { return prefs.get(key); }
                public void put(String key, String value) {
                    if (left[0]-- <= 0) throw new IllegalStateException("The tablet died");
                    if (value == null) prefs.remove(key); else prefs.put(key, value);
                }
            };
            boolean died = false;
            try { new ToolSlots(dies, new Random(4)); } catch (IllegalStateException e) { died = true; }
            ToolSlots slots = new ToolSlots(store(), new Random(4));
            check(prefs.get("tool_slots").equals(whole.get("tool_slots")) && prefs.get("brush_library").equals(whole.get("brush_library")), "Cut short after " + writes + " writes, the slots and library convert whole");
            for (String key : new String[]{ToolSwatches.CURRENT, ToolSwatches.CURRENT_PX})
                check(java.util.Objects.equals(prefs.get(key), whole.get(key)), "and " + key + " is the same, after " + writes + " writes");
            if (!died) break;
        }
    }
    static void unreadable() {
        prefs = new HashMap<>();
        prefs.put("tool_slots", "@INK\njunk\n\n@PENCIL");
        ToolSlots slots = new ToolSlots(store(), new Random(6));
        check(slots.brush(0).name().equals("Fineliner") && slots.brush(1).sameAs(ToolSlots.first(1)) && slots.brush(3).sameAs(ToolSlots.first(3))
                && slots.brush(5).sameAs(ToolSlots.first(5)), "A slot that doesn't read holds what a first launch starts with");
        slots.use(1, old(3));
        check(prefs.get("tool_slots").split("\n").length == ToolSlots.COUNT && slots.brush(4).sameAs(ToolSlots.first(4)), "Writing a slot writes all eight");
        prefs.put("tool_slots", "x\tdapper-fig.I610gavij3f7q16o0000006m0.e1\n@");
        check(new ToolSlots(store(), new Random(6)).brush(0).sameAs(ToolSlots.first(0)), "A broken old code never crashes");
    }
    static void smudge() {
        prefs = new HashMap<>();
        ToolSlots slots = old(store(), new Random(4));
        check(ToolSlots.ERASER == ToolSlots.COUNT && ToolSlots.SMUDGE == ToolSlots.COUNT + 1 && ToolSlots.STAMP == ToolSlots.COUNT + 2 && ToolSlots.TOOLS == ToolSlots.COUNT + 3, "The bar's tools: eight slots, the eraser, the smudge tool, the stamp tool");
        Brush stump = slots.smudge();
        check(stump.name().equals(ToolSlots.SMUDGE_FIRST) && ToolPresets.find(stump) != null && ToolSlots.smudges(stump), "The smudge tool starts as the Blend stump Basic tool, a smudge brush");
        for (int i = 0; i < ToolSlots.COUNT; i++) check(!ToolSlots.smudges(slots.brush(i)), "A first slot's brush (" + slots.brush(i).name() + ") lays paint: no smudge brush");
        java.util.List<Brush> choices = slots.smudgeChoices();
        int basic = 0;
        for (ToolPresets.Preset p : ToolPresets.all()) if (ToolSlots.smudges(p.brush())) basic++;
        check(basic >= 1 && choices.size() == basic && choices.get(0).sameAs(stump), "The chooser: the Basic tools that smudge (" + basic + "), the Blend stump first");
        for (Brush b : choices) check(ToolSlots.smudges(b), b.name() + " in the chooser smudges");
        // A saved smudge brush (a Stump with its own name) comes after the Basic tools; a saved brush that lays paint never shows.
        ModularBrush own = stump.parts.copy();
        own.name = "My stump";
        Brush saved = Brush.of(own);
        slots.library.save(saved);
        slots.library.save(style("Watercolor", 7));
        choices = slots.smudgeChoices();
        check(choices.size() == basic + 1 && choices.get(basic).sameAs(saved), "A saved smudge brush follows the Basic tools; a saved paint doesn't show");
        check(slots.useSmudge(saved) && slots.smudge().sameAs(saved) && !slots.useSmudge(saved), "The smudge tool takes a chooser's brush; the one it holds changes nothing");
        check(new ToolSlots(store(), new Random(5)).smudge().sameAs(saved), "and keeps it across a restart");
        check(slots.brush(0).name().equals("Fineliner"), "The slots are untouched");
        // A brush no longer in the library or the Basic tools still shows, first, so the chooser always holds the tool's own.
        prefs.remove(BrushLibrary.LIBRARY);
        slots = new ToolSlots(store(), new Random(6));
        Brush held = slots.smudge();
        check(held.sameAs(saved) && slots.smudgeChoices().get(0).sameAs(held), "The tool's own brush shows first when the chooser wouldn't hold it otherwise");
        // Its icon follows what its brush does (the owner, 2026-10-19): a blur for the blending stump, a smearing finger for a push, the stump for the rest.
        java.util.Map<String, ToolSlots.SmudgeKind> kinds = new java.util.HashMap<>();
        for (ToolPresets.Preset p : ToolPresets.all()) if (ToolSlots.smudges(p.brush())) kinds.put(p.name, ToolSlots.smudgeKind(p.brush()));
        check(kinds.get("Blend stump") == ToolSlots.SmudgeKind.BLUR && kinds.get("Stump") == ToolSlots.SmudgeKind.PUSH && kinds.get("Wash comb") == ToolSlots.SmudgeKind.STUMP,
                "The Blend stump blurs (Soften 1), the Stump pushes (Smear, Soften 0), the Wash comb combs: " + kinds);
        for (PartChoice carry : new PartChoice[]{PartChoice.LIFT, PartChoice.DRAG}) {
            ModularBrush b = stump.parts.copy();
            b.set(BrushPart.CARRY, new ModularBrush.Part(carry));
            check(ToolSlots.smudgeKind(Brush.of(b)) == (carry == PartChoice.DRAG ? ToolSlots.SmudgeKind.PUSH : ToolSlots.SmudgeKind.STUMP), carry + ": " + ToolSlots.smudgeKind(Brush.of(b)));
        }
    }
    /** The stamp tool (2026-10-21): a Stamp of its own, changed as a slot is (Undo), kept apart from the slots. */
    static void stamp() {
        prefs = new HashMap<>();
        ToolSlots slots = old(store(), new Random(4));
        Brush first = slots.stamp(), slot0 = slots.brush(0);
        check(first.parts.stamps() && first.parts.nib == 0 && slots.brush(ToolSlots.STAMP).sameAs(first), "The stamp tool starts as a Stamp with no nib cut (a round stamp)");
        check(ToolSlots.key(ToolSlots.STAMP).equals(ToolSlots.STAMP_KEY) && !ToolSlots.key(ToolSlots.STAMP).equals(ToolSlots.key(0)), "Its selection is its own");
        slots.frozen = true;
        Brush cut = Brush.of(first.parts.withNib(1234));
        check(slots.use(ToolSlots.STAMP, cut) && slots.stamp().sameAs(cut), "A cut nib is the stamp tool's, a locked notebook's toolbar or not");
        check(new ToolSlots(store(), new Random(5)).stamp().sameAs(cut), "and stays across a restart");
        check(slots.brush(0).sameAs(slot0) && prefs.get("tool_slots").split("\n").length == ToolSlots.COUNT, "The slots are untouched");
        check(slots.undo() == ToolSlots.STAMP && slots.stamp().sameAs(first), "Undo takes the cut back");
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    /**
     * Each tool's own size and grey (the owner, 2026-10-27: "i think we need per tool memory for size and shade"): set for
     * one, the others keep theirs; a brush picked into a slot, a roll, Undo and a kit keep the slot's; kept across a
     * restart; a notebook's own toolbar brings its own, and leaving it brings the app's back.
     */
    static void perTool() {
        prefs = new HashMap<>();
        ToolSlots slots = old(store(), new Random(1));
        ToolSwatches s = slots.swatches;
        s.useSize(ToolSlots.key(1), 9); s.useGrey(ToolSlots.key(1), .4);
        s.chooseSize(ToolSlots.key(2), 3); s.useGrey(ToolSlots.STAMP_KEY, .7);
        check(s.look("slot1", slots.brush(1)).px == 9 && s.look("slot1", slots.brush(1)).darkness == .4, "Slot 1 at its own 9 px and grey .4");
        check(s.look("slot2", slots.brush(2)).px == s.size(3) && s.look("slot2", slots.brush(2)).darkness == .216, "Slot 2 at the size it picked, its grey untouched");
        check(s.look("slot0", slots.brush(0)).px == 44 && s.look("slot0", slots.brush(0)).darkness == .216 && s.look(ToolSlots.STAMP_KEY, slots.stamp()).px == 44
                && s.look(ToolSlots.STAMP_KEY, slots.stamp()).darkness == .7 && s.look(ToolSwatches.ERASER_KEY, null).px == 24, "The others keep theirs; the stamp tool its own grey");
        slots.use(1, style("Watercolor", 3)); slots.roll(1); slots.undo(); slots.useAll(new Brush[]{style("Gouache", 4), style("Charcoal", 5)});
        for (int i = 0; i < ToolSlots.COUNT; i++) {
            ToolSwatches.Look look = s.look(ToolSlots.key(i), slots.brush(i));
            check(look.px == (i == 1 ? 9 : i == 2 ? s.size(3) : 44) && look.darkness == (i == 1 ? .4 : .216), "A pick, a roll, Undo and a kit keep slot " + i + "'s size and grey");
        }
        ToolSwatches again = new ToolSlots(store(), new Random(2)).swatches;
        check(again.look("slot1", slots.brush(1)).px == 9 && again.look("slot1", slots.brush(1)).darkness == .4 && again.look("slot2", slots.brush(2)).px == s.size(3), "Kept across a restart");
        ToolSlots.Toolbar own = slots.toolbar();
        own.darkness[3] = .9; own.px[3] = 50;
        // Reset to Default (2026-10-27): the Basic tool's own settings back, an Undo step, the slot's size and grey kept.
        ToolPresets.Preset wax = ToolKits.preset("Wax crayon");
        ModularBrush changed = wax.brush().parts.copy(); changed.part(BrushPart.TIP).set(PartDial.WEIGHT, 1.3); Brush edited = Brush.of(changed);
        slots.use(1, edited);
        check(DrawLayout.edited(slots.brush(1)) == wax && slots.use(1, DrawLayout.edited(slots.brush(1)).brush()), "Reset puts the Basic tool back");
        check(slots.brush(1).write().equals(wax.brush().write()) && s.look("slot1", slots.brush(1)).px == 9 && s.look("slot1", slots.brush(1)).darkness == .4,
                "Reset restores its code at slot 1's own size and grey");
        slots.undo();
        check(slots.brush(1).sameAs(edited), "Undo takes the reset back");
        check(slots.enter(own) && s.look("slot3", slots.brush(3)).px == 50 && s.look("slot3", slots.brush(3)).darkness == .9, "A notebook's own toolbar brings its sizes and greys");
        s.useGrey(ToolSlots.key(3), .6);
        slots.leave();
        check(s.look("slot3", slots.brush(3)).px == 44 && s.look("slot3", slots.brush(3)).darkness == .216 && s.look("slot1", slots.brush(1)).px == 9, "Leaving brings the app's back");
    }
    /** The first launch since the one size and grey (2026-10-24 to 27): they become every slot's and the stamp tool's own, once. */
    static void currentMigration() {
        prefs = new HashMap<>();
        old(store(), new Random(1)).swatches.useSize(ToolSlots.SMUDGE_KEY, 33);
        // A build with the one size and grey, each slot's own from before it still kept.
        prefs.put(ToolSwatches.PX + "slot0", "120"); prefs.put(ToolSwatches.DARK + "slot0", "0.9");
        prefs.put(ToolSwatches.CURRENT_PX, "30"); prefs.put(ToolSwatches.CURRENT, "0.45");
        ToolSlots slots = new ToolSlots(store(), new Random(1));
        for (int i = 0; i < ToolSlots.COUNT; i++)
            check(slots.swatches.look(ToolSlots.key(i), slots.brush(i)).px == 30 && slots.swatches.look(ToolSlots.key(i), slots.brush(i)).darkness == .45, "Slot " + i + " at the one size and grey it drew at");
        check(slots.swatches.look(ToolSlots.STAMP_KEY, slots.stamp()).px == 30 && slots.swatches.look(ToolSlots.STAMP_KEY, slots.stamp()).darkness == .45, "the stamp tool too");
        check(slots.swatches.look(ToolSlots.SMUDGE_KEY, slots.smudge()).px == 33 && slots.swatches.look(ToolSwatches.ERASER_KEY, null).px == 24, "The smudge tool and the eraser keep theirs");
        check(!prefs.containsKey(ToolSwatches.CURRENT) && !prefs.containsKey(ToolSwatches.CURRENT_PX), "The one size and grey let go");
        String before = prefs.toString();
        new ToolSlots(store(), new Random(2));
        check(prefs.toString().equals(before), "Once: a relaunch changes nothing");
        // A wash's grey kept before washes reached black, and no one grey: the grey it laid then.
        prefs.remove(ToolSwatches.DARK + "slot7"); prefs.put("inkwash_grey_slot7", "1");
        ToolSlots washed = new ToolSlots(store(), new Random(1));
        check(near(washed.swatches.look("slot7", washed.brush(7)).darkness, ModularSizes.sinceBlack(washed.brush(7).parts, 1)) && ModularSizes.washes(washed.brush(7).parts),
                "An Ink wash kept before washes reached black: the grey it laid");
    }
}
